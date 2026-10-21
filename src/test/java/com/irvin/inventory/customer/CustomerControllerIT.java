package com.irvin.inventory.customer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.irvin.inventory.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

class CustomerControllerIT extends AbstractIntegrationTest {

    @Test
    void sellerCreatesCustomerWithNormalizedFields() throws Exception {
        String sellerToken = loginOk(createSeller().email(), DEFAULT_PASSWORD).accessToken();
        String taxId = randomCompanyTaxId();

        postJson("/api/v1/customers", """
                {"name": "  Fonda Lupita  ", "taxId": "%s", "email": " Lupita@Mail.COM ", "phone": "55 1234 5678",
                 "fiscalPostalCode": " 06600 ", "taxRegime": " 601 "}
                """.formatted(taxId.toLowerCase()), sellerToken)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Fonda Lupita"))
                .andExpect(jsonPath("$.taxId").value(taxId))
                .andExpect(jsonPath("$.fiscalPostalCode").value("06600"))
                .andExpect(jsonPath("$.taxRegime").value("601"))
                .andExpect(jsonPath("$.email").value("lupita@mail.com"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.walkIn").value(false))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void duplicateOrWalkInTaxIdConflicts() throws Exception {
        String token = adminAccessToken();
        String taxId = randomCompanyTaxId();

        postJson("/api/v1/customers", customerBody(taxId), token).andExpect(status().isCreated());
        postJson("/api/v1/customers", customerBody(taxId.toLowerCase()), token).andExpect(status().isConflict());
        postJson("/api/v1/customers", customerBody(Customer.WALK_IN_TAX_ID), token).andExpect(status().isConflict());

        Long other = createCustomer();
        putJson("/api/v1/customers/" + other, """
                {"name": "Other", "taxId": "%s", %s, "active": true, "version": 0}
                """.formatted(taxId, fiscalFields(taxId)), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Tax ID already exists"));
    }

    @Test
    void walkInCustomerCannotBeModified() throws Exception {
        String token = adminAccessToken();
        Long walkInId = jdbcTemplate.queryForObject(
                "SELECT id FROM customer WHERE tax_id = ?", Long.class, Customer.WALK_IN_TAX_ID);

        getJson("/api/v1/customers/" + walkInId, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("PUBLICO EN GENERAL"))
                .andExpect(jsonPath("$.walkIn").value(true));
        putJson("/api/v1/customers/" + walkInId, updateBody("Renamed", false, 0), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The walk-in customer can't be modified"));
        getJson("/api/v1/customers/" + walkInId, token).andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void adminUpdatesAnyFieldButStaleVersionsAndUnknownIdsFail() throws Exception {
        String adminToken = adminAccessToken();
        Long id = createCustomer();
        String taxId = randomCompanyTaxId();

        putJson("/api/v1/customers/" + id, """
                {"name": "Renamed", "taxId": "%s", %s, "active": false, "version": 0}
                """.formatted(taxId, fiscalFields(taxId)), adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.taxId").value(taxId))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.version").value(1));
        putJson("/api/v1/customers/" + id, updateBody("Again", true, 0), adminToken)
                .andExpect(status().isConflict());
        putJson("/api/v1/customers/999999", updateBody("X", true, 0), adminToken)
                .andExpect(status().isNotFound());
    }

    @Test
    void sellerEditsContactDataButNotTaxIdOrActiveFlag() throws Exception {
        String sellerToken = loginOk(createSeller().email(), DEFAULT_PASSWORD).accessToken();
        String taxId = randomCompanyTaxId();
        Long id = readId(postJson("/api/v1/customers", customerBody(taxId), sellerToken)
                .andExpect(status().isCreated()));

        // Same tax ID (any letter case) and active flag: allowed.
        putJson("/api/v1/customers/" + id, """
                {"name": "Renamed", "taxId": "%s", %s, "phone": "5512345678", "active": true, "version": 0}
                """.formatted(taxId.toLowerCase(), fiscalFields(taxId)), sellerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("5512345678"));

        String forbidden = "Only a manager can change a customer's tax ID or active status";
        String otherTaxId = randomCompanyTaxId();
        putJson("/api/v1/customers/" + id, """
                {"name": "Renamed", "taxId": "%s", %s, "active": true, "version": 1}
                """.formatted(otherTaxId, fiscalFields(otherTaxId)), sellerToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(forbidden));
        putJson("/api/v1/customers/" + id, updateBody("Renamed", true, 1), sellerToken)
                .andExpect(status().isForbidden());
        putJson("/api/v1/customers/" + id, """
                {"name": "Renamed", "taxId": "%s", %s, "active": false, "version": 1}
                """.formatted(taxId, fiscalFields(taxId)), sellerToken)
                .andExpect(status().isForbidden());

        getJson("/api/v1/customers/" + id, sellerToken)
                .andExpect(jsonPath("$.taxId").value(taxId))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void invalidFieldsReturnFieldErrors() throws Exception {
        postJson("/api/v1/customers", """
                {"name": " ", "taxId": "NOT-A-TAX-ID", "email": "bad", "phone": "call me"}
                """, adminAccessToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.taxId").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.phone").exists());
    }

    @Test
    void fiscalDataIsStoredWithTheTaxIdAndClearedWithIt() throws Exception {
        String token = adminAccessToken();
        String taxId = randomIndividualTaxId();

        Long id = readId(postJson("/api/v1/customers", fiscalBody(taxId, "44100", "612"), token)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fiscalPostalCode").value("44100"))
                .andExpect(jsonPath("$.taxRegime").value("612")));

        // Blank fields mean missing, so all three are cleared together.
        putJson("/api/v1/customers/" + id, """
                {"name": "Customer", "taxId": " ", "fiscalPostalCode": "", "taxRegime": " ", "active": true,
                 "version": 0}
                """, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxId").doesNotExist())
                .andExpect(jsonPath("$.fiscalPostalCode").doesNotExist())
                .andExpect(jsonPath("$.taxRegime").doesNotExist());
    }

    @Test
    void taxIdAndFiscalDataGoTogether() throws Exception {
        String token = adminAccessToken();
        String taxId = randomCompanyTaxId();
        String incomplete = CustomerService.INCOMPLETE_FISCAL_DATA_MESSAGE;

        postJson("/api/v1/customers", """
                {"name": "Customer", "taxId": "%s"}
                """.formatted(taxId), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(incomplete));
        postJson("/api/v1/customers", """
                {"name": "Customer", "taxId": "%s", "fiscalPostalCode": "06600"}
                """.formatted(taxId), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(incomplete));
        postJson("/api/v1/customers", """
                {"name": "Customer", "taxId": "%s", "taxRegime": "601"}
                """.formatted(taxId), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(incomplete));
        postJson("/api/v1/customers", """
                {"name": "Customer", "fiscalPostalCode": "06600", "taxRegime": "601"}
                """, token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(incomplete));
        getJson("/api/v1/customers?search=" + taxId, token).andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void customerWithTaxIdButNoFiscalDataMustCompleteItOnEdit() throws Exception {
        String sellerToken = loginOk(createSeller().email(), DEFAULT_PASSWORD).accessToken();
        String taxId = randomCompanyTaxId();
        // Saved before customers had fiscal data.
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO customer (name, tax_id) VALUES ('Legacy', ?) RETURNING id", Long.class, taxId);

        putJson("/api/v1/customers/" + id, """
                {"name": "Legacy", "taxId": "%s", "phone": "5512345678", "active": true, "version": 0}
                """.formatted(taxId), sellerToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(CustomerService.INCOMPLETE_FISCAL_DATA_MESSAGE));
        // Unlike the tax ID, fiscal data isn't restricted to managers: a seller can complete it.
        putJson("/api/v1/customers/" + id, """
                {"name": "Legacy", "taxId": "%s", %s, "phone": "5512345678", "active": true, "version": 0}
                """.formatted(taxId, fiscalFields(taxId)), sellerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fiscalPostalCode").value("06600"))
                .andExpect(jsonPath("$.taxRegime").value("601"));
    }

    @Test
    void customerWithTaxIdButNoFiscalDataCanStillBeDeactivatedAsIs() throws Exception {
        String token = adminAccessToken();
        String taxId = randomCompanyTaxId();
        // Saved before customers had fiscal data.
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO customer (name, tax_id) VALUES ('Legacy', ?) RETURNING id", Long.class, taxId);

        putJson("/api/v1/customers/" + id, """
                {"name": "Legacy", "taxId": "%s", "active": false, "version": 0}
                """.formatted(taxId), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.fiscalPostalCode").doesNotExist());
    }

    @Test
    void invalidFiscalDataIsRejected() throws Exception {
        String token = adminAccessToken();
        String company = randomCompanyTaxId();
        String individual = randomIndividualTaxId();

        postJson("/api/v1/customers", fiscalBody(company, "6600", "601"), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fiscalPostalCode").exists());
        postJson("/api/v1/customers", fiscalBody(company, "O6600", "601"), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fiscalPostalCode").exists());
        postJson("/api/v1/customers", fiscalBody(company, "06600", "999"), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(CustomerService.UNKNOWN_TAX_REGIME_MESSAGE));
        // Wages (605) is for individuals only, general law (601) for companies only.
        postJson("/api/v1/customers", fiscalBody(company, "06600", "605"), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(CustomerService.COMPANY_TAX_REGIME_MESSAGE));
        postJson("/api/v1/customers", fiscalBody(individual, "06600", "601"), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(CustomerService.INDIVIDUAL_TAX_REGIME_MESSAGE));

        // RESICO (626) applies to both.
        postJson("/api/v1/customers", fiscalBody(company, "06600", "626"), token).andExpect(status().isCreated());
        postJson("/api/v1/customers", fiscalBody(individual, "06600", "626"), token).andExpect(status().isCreated());
    }

    @Test
    void searchMatchesNameOrTaxIdAndActiveFlag() throws Exception {
        String token = adminAccessToken();
        String tag = "Cus" + UUID.randomUUID().toString().substring(0, 8);
        String taxId = randomCompanyTaxId();
        postJson("/api/v1/customers", """
                {"name": "%s Abarrotes", "taxId": "%s", %s}
                """.formatted(tag, taxId, fiscalFields(taxId)), token).andExpect(status().isCreated());

        getJson("/api/v1/customers?search=" + tag.toLowerCase(), token)
                .andExpect(jsonPath("$.content.length()").value(1));
        getJson("/api/v1/customers?search=" + taxId, token)
                .andExpect(jsonPath("$.content[0].name").value(tag + " Abarrotes"));
        getJson("/api/v1/customers?search=" + tag + "&active=false", token)
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void onlyCustomersWithoutSalesCanBeDeleted() throws Exception {
        String adminToken = adminAccessToken();
        Long unused = createCustomer();
        Long withSale = createCustomer();
        Long product = createPricedProduct("1", "0");
        addStock(product, 1);
        String sellerToken = sellerToken();
        sell("""
                {"customerId": %d, "paymentMethod": "CASH", "items": [{"productId": %d, "quantity": 1}]}
                """.formatted(withSale, product), sellerToken)
                .andExpect(status().isCreated());
        Long walkInId = jdbcTemplate.queryForObject(
                "SELECT id FROM customer WHERE tax_id = ?", Long.class, Customer.WALK_IN_TAX_ID);

        mockMvc.perform(withToken(delete("/api/v1/customers/{id}", unused), sellerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(withToken(delete("/api/v1/customers/{id}", unused), adminToken))
                .andExpect(status().isNoContent());
        getJson("/api/v1/customers/" + unused, adminToken).andExpect(status().isNotFound());
        mockMvc.perform(withToken(delete("/api/v1/customers/{id}", withSale), adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(CustomerService.IN_USE_MESSAGE));
        mockMvc.perform(withToken(delete("/api/v1/customers/{id}", walkInId), adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The walk-in customer can't be deleted"));
        mockMvc.perform(withToken(delete("/api/v1/customers/{id}", 999_999_999L), adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void anonymousRequestsAreRejected() throws Exception {
        postJson("/api/v1/customers", customerBody(randomCompanyTaxId())).andExpect(status().isUnauthorized());
    }

    private static String customerBody(String taxId) {
        return """
                {"name": "Customer", "taxId": "%s", %s}
                """.formatted(taxId, fiscalFields(taxId));
    }

    private static String fiscalBody(String taxId, String fiscalPostalCode, String taxRegime) {
        return """
                {"name": "Customer", "taxId": "%s", "fiscalPostalCode": "%s", "taxRegime": "%s"}
                """.formatted(taxId, fiscalPostalCode, taxRegime);
    }

    // Valid fiscal data for the tax ID: general law for a company (12 characters), no obligations otherwise.
    private static String fiscalFields(String taxId) {
        return "\"fiscalPostalCode\": \"06600\", \"taxRegime\": \"%s\""
                .formatted(taxId.length() == 12 ? "601" : "616");
    }

    // 4 letters, so 13 characters: an individual's tax ID.
    private static String randomIndividualTaxId() {
        return "P" + randomCompanyTaxId();
    }

    private static String updateBody(String name, boolean active, long version) {
        return """
                {"name": "%s", "active": %s, "version": %d}
                """.formatted(name, active, version);
    }
}

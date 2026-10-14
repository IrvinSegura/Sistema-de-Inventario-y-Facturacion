package com.irvin.inventory.customer;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.irvin.inventory.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class CustomerControllerIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void sellerCreatesCustomerWithNormalizedFields() throws Exception {
        String sellerToken = loginOk(createSeller().email(), DEFAULT_PASSWORD).accessToken();
        String taxId = randomCompanyTaxId();

        postJson("/api/v1/customers", """
                {"name": "  Fonda Lupita  ", "taxId": "%s", "email": " Lupita@Mail.COM ", "phone": "55 1234 5678"}
                """.formatted(taxId.toLowerCase()), sellerToken)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Fonda Lupita"))
                .andExpect(jsonPath("$.taxId").value(taxId))
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
                {"name": "Other", "taxId": "%s", "active": true, "version": 0}
                """.formatted(taxId), token)
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
                {"name": "Renamed", "taxId": "%s", "active": false, "version": 0}
                """.formatted(taxId), adminToken)
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
                {"name": "Renamed", "taxId": "%s", "phone": "5512345678", "active": true, "version": 0}
                """.formatted(taxId.toLowerCase()), sellerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("5512345678"));

        String forbidden = "Only an admin can change a customer's tax ID or active status";
        putJson("/api/v1/customers/" + id, """
                {"name": "Renamed", "taxId": "%s", "active": true, "version": 1}
                """.formatted(randomCompanyTaxId()), sellerToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(forbidden));
        putJson("/api/v1/customers/" + id, updateBody("Renamed", true, 1), sellerToken)
                .andExpect(status().isForbidden());
        putJson("/api/v1/customers/" + id, """
                {"name": "Renamed", "taxId": "%s", "active": false, "version": 1}
                """.formatted(taxId), sellerToken)
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
    void searchMatchesNameOrTaxIdAndActiveFlag() throws Exception {
        String token = adminAccessToken();
        String tag = "Cus" + UUID.randomUUID().toString().substring(0, 8);
        String taxId = randomCompanyTaxId();
        postJson("/api/v1/customers", """
                {"name": "%s Abarrotes", "taxId": "%s"}
                """.formatted(tag, taxId), token).andExpect(status().isCreated());

        getJson("/api/v1/customers?search=" + tag.toLowerCase(), token)
                .andExpect(jsonPath("$.content.length()").value(1));
        getJson("/api/v1/customers?search=" + taxId, token)
                .andExpect(jsonPath("$.content[0].name").value(tag + " Abarrotes"));
        getJson("/api/v1/customers?search=" + tag + "&active=false", token)
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void anonymousRequestsAreRejected() throws Exception {
        postJson("/api/v1/customers", customerBody(randomCompanyTaxId())).andExpect(status().isUnauthorized());
    }

    private static String customerBody(String taxId) {
        return """
                {"name": "Customer", "taxId": "%s"}
                """.formatted(taxId);
    }

    private static String updateBody(String name, boolean active, long version) {
        return """
                {"name": "%s", "active": %s, "version": %d}
                """.formatted(name, active, version);
    }
}

package com.irvin.inventory.purchasing;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.irvin.inventory.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

class SupplierControllerIT extends AbstractIntegrationTest {

    @Test
    void adminCreatesSupplierWithNormalizedFields() throws Exception {
        String taxId = randomCompanyTaxId();

        postJson("/api/v1/suppliers", """
                {"name": "  Bimbo  ", "taxId": "%s", "email": " Ventas@Bimbo.COM ", "phone": "+52 55 1234 5678"}
                """.formatted(taxId.toLowerCase()), adminAccessToken())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Bimbo"))
                .andExpect(jsonPath("$.taxId").value(taxId))
                .andExpect(jsonPath("$.email").value("ventas@bimbo.com"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void duplicateTaxIdConflicts() throws Exception {
        String adminToken = adminAccessToken();
        String taxId = randomCompanyTaxId();

        postJson("/api/v1/suppliers", supplierBody(taxId), adminToken).andExpect(status().isCreated());
        postJson("/api/v1/suppliers", supplierBody(taxId.toLowerCase()), adminToken).andExpect(status().isConflict());
    }

    @Test
    void invalidFieldsReturnFieldErrors() throws Exception {
        postJson("/api/v1/suppliers", """
                {"name": "", "taxId": "NOT-AN-RFC", "email": "bad", "phone": "call me"}
                """, adminAccessToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.taxId").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.phone").exists());
    }

    @Test
    void sellerCannotAccessSuppliers() throws Exception {
        String sellerToken = loginOk(createSeller().email(), DEFAULT_PASSWORD).accessToken();

        getJson("/api/v1/suppliers", sellerToken).andExpect(status().isForbidden());
        postJson("/api/v1/suppliers", supplierBody(randomCompanyTaxId()), sellerToken).andExpect(status().isForbidden());
    }

    @Test
    void updateRejectsStaleVersionAndUnknownId() throws Exception {
        String adminToken = adminAccessToken();
        Long id = createSupplier();

        putJson("/api/v1/suppliers/" + id, updateBody("Renamed", false, 0), adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.version").value(1));
        putJson("/api/v1/suppliers/" + id, updateBody("Again", true, 0), adminToken)
                .andExpect(status().isConflict());
        putJson("/api/v1/suppliers/999999", updateBody("X", true, 0), adminToken)
                .andExpect(status().isNotFound());
    }

    @Test
    void searchMatchesNameOrTaxIdAndActiveFlag() throws Exception {
        String adminToken = adminAccessToken();
        String tag = "Sup" + UUID.randomUUID().toString().substring(0, 8);
        String taxId = randomCompanyTaxId();
        postJson("/api/v1/suppliers", """
                {"name": "%s Lala", "taxId": "%s"}
                """.formatted(tag, taxId), adminToken).andExpect(status().isCreated());

        getJson("/api/v1/suppliers?search=" + tag.toLowerCase(), adminToken)
                .andExpect(jsonPath("$.content.length()").value(1));
        getJson("/api/v1/suppliers?search=" + taxId, adminToken)
                .andExpect(jsonPath("$.content[0].name").value(tag + " Lala"));
        getJson("/api/v1/suppliers?search=" + tag + "&active=false", adminToken)
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    private static String supplierBody(String taxId) {
        return """
                {"name": "Supplier", "taxId": "%s"}
                """.formatted(taxId);
    }

    private static String updateBody(String name, boolean active, long version) {
        return """
                {"name": "%s", "active": %s, "version": %d}
                """.formatted(name, active, version);
    }
}

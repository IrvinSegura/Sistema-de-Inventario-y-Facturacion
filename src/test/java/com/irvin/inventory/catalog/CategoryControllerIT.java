package com.irvin.inventory.catalog;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.irvin.inventory.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

class CategoryControllerIT extends AbstractIntegrationTest {

    @Test
    void anyUserListsSeededCategoriesWithTheirDefaultTaxRate() throws Exception {
        String sellerToken = loginOk(createSeller().email(), DEFAULT_PASSWORD).accessToken();

        getJson("/api/v1/categories", sellerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Pantry')].defaultTaxRate").value(0.0))
                .andExpect(jsonPath("$[?(@.name == 'Beverages')].defaultTaxRate").value(0.16));
    }

    @Test
    void adminCreatesCategoryAndNamesAreUniqueIgnoringCase() throws Exception {
        String name = "Frozen " + UUID.randomUUID();
        String adminToken = adminAccessToken();

        postJson("/api/v1/categories", categoryBody(name, "0.16"), adminToken)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(0));
        postJson("/api/v1/categories", categoryBody(name.toUpperCase(), "0.16"), adminToken)
                .andExpect(status().isConflict());
    }

    @Test
    void sellerCannotWriteCategories() throws Exception {
        String sellerToken = loginOk(createSeller().email(), DEFAULT_PASSWORD).accessToken();

        postJson("/api/v1/categories", categoryBody("Any " + UUID.randomUUID(), "0"), sellerToken)
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidTaxRateIsRejected() throws Exception {
        postJson("/api/v1/categories", categoryBody("Bad " + UUID.randomUUID(), "1.5"), adminAccessToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.defaultTaxRate").exists());
    }

    @Test
    void categoryWithActiveProductsCannotBeDeactivated() throws Exception {
        String name = "Temp " + UUID.randomUUID();
        Long id = createCategory(name);
        createProduct(id, 0);

        putJson("/api/v1/categories/" + id, updateBody(name, false, 0), adminAccessToken())
                .andExpect(status().isConflict());
    }

    @Test
    void inactiveCategoriesAreHiddenByDefaultAndCanBeReactivated() throws Exception {
        String adminToken = adminAccessToken();
        String name = "Hidden " + UUID.randomUUID();
        Long id = createCategory(name);

        putJson("/api/v1/categories/" + id, updateBody(name, false, 0), adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.version").value(1));
        getJson("/api/v1/categories", adminToken)
                .andExpect(jsonPath("$[?(@.name == '" + name + "')]").isEmpty());
        getJson("/api/v1/categories?includeInactive=true", adminToken)
                .andExpect(jsonPath("$[?(@.name == '" + name + "')]").isNotEmpty());

        putJson("/api/v1/categories/" + id, updateBody(name, true, 1), adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void updateRejectsStaleVersionDuplicateNameAndUnknownId() throws Exception {
        String adminToken = adminAccessToken();
        String name = "Edit " + UUID.randomUUID();
        Long id = createCategory(name);

        putJson("/api/v1/categories/" + id, updateBody(name + " renamed", true, 0), adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        // Another admin still holding version 0 must reload instead of overwriting.
        putJson("/api/v1/categories/" + id, updateBody(name, true, 0), adminToken)
                .andExpect(status().isConflict());
        putJson("/api/v1/categories/" + id, updateBody("PANTRY", true, 1), adminToken)
                .andExpect(status().isConflict());
        putJson("/api/v1/categories/999999", updateBody(name, true, 0), adminToken)
                .andExpect(status().isNotFound());
    }

    private static String categoryBody(String name, String taxRate) {
        return """
                {"name": "%s", "defaultTaxRate": %s}
                """.formatted(name, taxRate);
    }

    private static String updateBody(String name, boolean active, long version) {
        return """
                {"name": "%s", "defaultTaxRate": 0, "active": %s, "version": %d}
                """.formatted(name, active, version);
    }
}

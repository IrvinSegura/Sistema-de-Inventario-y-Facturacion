package com.irvin.inventory.report;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.irvin.inventory.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

class StockReportControllerIT extends AbstractIntegrationTest {

    @Test
    void listsActiveProductsAtOrBelowTheMinimumMostUrgentFirst() throws Exception {
        // Own category so products from other tests don't show up.
        Long categoryId = createCategory("Reports " + UUID.randomUUID());
        Long atMinimum = createProduct("At minimum", categoryId, 5);
        Long farBelow = createProduct("Far below", categoryId, 10);
        Long aboveMinimum = createProduct("Above minimum", categoryId, 3);
        Long inactive = createProduct("Inactive", categoryId, 10);
        // Stock 0 with a small minimum: smaller shortage than farBelow, but it can't be sold at all.
        Long outOfStock = createProduct("Out of stock", categoryId, 2);
        addStock(atMinimum, 5);
        addStock(farBelow, 2);
        addStock(aboveMinimum, 4);
        deactivateProduct(inactive);
        String url = "/api/v1/stock-reports/low-stock?categoryId=" + categoryId;

        // Any user can read it; the client sort is ignored.
        getJson(url + "&sort=name,asc", sellerToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].productId").value(outOfStock))
                .andExpect(jsonPath("$.content[0].shortage").value(2))
                .andExpect(jsonPath("$.content[1].productId").value(farBelow))
                .andExpect(jsonPath("$.content[1].stock").value(2))
                .andExpect(jsonPath("$.content[1].minStock").value(10))
                .andExpect(jsonPath("$.content[1].shortage").value(8))
                .andExpect(jsonPath("$.content[1].category.id").value(categoryId))
                .andExpect(jsonPath("$.content[2].productId").value(atMinimum))
                .andExpect(jsonPath("$.content[2].shortage").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(3));
        getJson(url + "&size=1&page=2", adminAccessToken())
                .andExpect(jsonPath("$.content[0].productId").value(atMinimum));
    }

    @Test
    void withoutCategoryFilterListsLowStockFromEveryCategory() throws Exception {
        // Default call of the UI: exercises the null categoryId branch of the query.
        Long product = createProduct(createCategory("Reports " + UUID.randomUUID()), 1);

        getJson("/api/v1/stock-reports/low-stock?size=100", sellerToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.productId == " + product + ")].shortage").value(1));
    }

    @Test
    void anonymousRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/stock-reports/low-stock")).andExpect(status().isUnauthorized());
    }
}

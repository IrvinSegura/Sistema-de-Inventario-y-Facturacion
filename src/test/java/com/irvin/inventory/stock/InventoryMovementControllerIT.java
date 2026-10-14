package com.irvin.inventory.stock;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvin.inventory.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

class InventoryMovementControllerIT extends AbstractIntegrationTest {

    private static final String MOVEMENTS = "/api/v1/inventory-movements";

    @Test
    void searchListsEveryStockChangeNewestFirst() throws Exception {
        String adminToken = adminAccessToken();
        Long product = createPricedProduct("1", "0");
        addStock(product, 10);
        String saleBody = """
                {"paymentMethod": "CASH", "items": [{"productId": %d, "quantity": 3}]}
                """.formatted(product);
        Long saleId = readId(sell(saleBody, sellerToken()).andExpect(status().isCreated()));
        postJson("/api/v1/sales/" + saleId + "/cancellation", """
                {"reason": "Customer changed their mind"}
                """, adminToken).andExpect(status().isOk());
        adjust(product, -2, "Damaged", adminToken);
        Long saleNumber = jdbcTemplate.queryForObject(
                "SELECT sale_number FROM sale WHERE id = ?", Long.class, saleId);
        String url = MOVEMENTS + "?productId=" + product;

        getJson(url, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(4))
                .andExpect(jsonPath("$.content[0].type").value("ADJUSTMENT"))
                .andExpect(jsonPath("$.content[0].quantity").value(-2))
                .andExpect(jsonPath("$.content[0].resultingStock").value(8))
                .andExpect(jsonPath("$.content[0].reason").value("Damaged"))
                .andExpect(jsonPath("$.content[1].type").value("SALE_CANCELLATION"))
                .andExpect(jsonPath("$.content[1].resultingStock").value(10))
                .andExpect(jsonPath("$.content[1].saleNumber").value(saleNumber))
                .andExpect(jsonPath("$.content[2].type").value("SALE"))
                .andExpect(jsonPath("$.content[2].quantity").value(-3))
                .andExpect(jsonPath("$.content[2].resultingStock").value(7))
                .andExpect(jsonPath("$.content[2].saleId").value(saleId))
                .andExpect(jsonPath("$.content[3].type").value("PURCHASE"))
                .andExpect(jsonPath("$.content[3].purchaseId").isNumber())
                .andExpect(jsonPath("$.content[3].saleId").doesNotExist())
                .andExpect(jsonPath("$.page.totalElements").value(4));
        getJson(url + "&type=SALE", adminToken)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].type").value("SALE"));
        // The client sort is ignored.
        getJson(url + "&size=2&page=1&sort=type,asc", adminToken)
                .andExpect(jsonPath("$.content[0].type").value("SALE"))
                .andExpect(jsonPath("$.content[1].type").value("PURCHASE"))
                .andExpect(jsonPath("$.page.totalElements").value(4));
    }

    @Test
    void typeFilterAppliesToTheTotalCountToo() throws Exception {
        String token = adminAccessToken();
        Long product = createPricedProduct("1", "0");
        addStock(product, 10);
        adjust(product, -1, "Damaged", token);
        adjust(product, -1, "Expired", token);

        // A second page forces the count query, which must apply the same filter.
        getJson(MOVEMENTS + "?productId=" + product + "&type=ADJUSTMENT&size=1&page=1", token)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].reason").value("Damaged"))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void dateRangeIncludesFromAndExcludesTo() throws Exception {
        String token = adminAccessToken();
        Long product = createPricedProduct("1", "0");
        addStock(product, 10);
        String body = adjust(product, -1, "Damaged", token);
        String movementDate = JsonPath.read(body, "$.movementDate");
        Long movementId = ((Number) JsonPath.read(body, "$.id")).longValue();

        mockMvc.perform(withToken(get(MOVEMENTS)
                        .queryParam("productId", product.toString())
                        .queryParam("from", movementDate), token))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(movementId));
        mockMvc.perform(withToken(get(MOVEMENTS)
                        .queryParam("productId", product.toString())
                        .queryParam("type", "ADJUSTMENT")
                        .queryParam("to", movementDate), token))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void invalidSearchesAreRejected() throws Exception {
        String token = adminAccessToken();
        Long product = createPricedProduct("1", "0");
        String url = MOVEMENTS + "?productId=" + product;

        getJson(MOVEMENTS, token).andExpect(status().isBadRequest());
        getJson(MOVEMENTS + "?productId=999999", token)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Product not found: 999999"));
        getJson(url + "&type=FOO", token).andExpect(status().isBadRequest());
        getJson(url + "&from=2025-01-01", token).andExpect(status().isBadRequest());
        getJson(url + "&from=2025-02-01T00:00:00Z&to=2025-01-01T00:00:00Z", token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("'from' must be before 'to'"));
        // ISO instants accept signed years beyond 9999; queryParam avoids URL-encoding the "+".
        mockMvc.perform(withToken(get(MOVEMENTS)
                        .queryParam("productId", product.toString())
                        .queryParam("to", "+300000-01-01T00:00:00Z"), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "Dates must be between 2000-01-01T00:00:00Z and 9999-12-30T00:00:00Z"));
        getJson(MOVEMENTS + "/999999999", token).andExpect(status().isNotFound());
    }

    @Test
    void sellerCannotReadMovements() throws Exception {
        String sellerToken = sellerToken();
        Long product = createPricedProduct("1", "0");
        addStock(product, 1);
        Long movementId = jdbcTemplate.queryForObject(
                "SELECT id FROM inventory_movement WHERE product_id = ?", Long.class, product);

        getJson(MOVEMENTS + "?productId=" + product, sellerToken).andExpect(status().isForbidden());
        getJson(MOVEMENTS + "/" + movementId, sellerToken).andExpect(status().isForbidden());
    }

    // Returns the created movement as JSON.
    private String adjust(Long productId, int quantity, String reason, String token) throws Exception {
        return postJson("/api/v1/stock-adjustments", """
                {"productId": %d, "quantity": %d, "reason": "%s"}
                """.formatted(productId, quantity, reason), token)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }
}

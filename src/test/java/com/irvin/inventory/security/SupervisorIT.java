package com.irvin.inventory.security;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.irvin.inventory.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

// SUPERVISOR runs the store like an admin, except user accounts.
class SupervisorIT extends AbstractIntegrationTest {

    @Test
    void supervisorManagesCatalogAndStock() throws Exception {
        String token = supervisorToken();
        Long categoryId = readId(postJson("/api/v1/categories", """
                {"name": "Supervised %s", "defaultTaxRate": 0}
                """.formatted(UUID.randomUUID()), token)
                .andExpect(status().isCreated()));
        Long productId = readId(postJson("/api/v1/products", """
                {"sku": "%s", "name": "Supervised %s", "categoryId": %d, "salePrice": 5, "minStock": 0}
                """.formatted(uniqueSku(), UUID.randomUUID(), categoryId), token)
                .andExpect(status().isCreated()));

        postJson("/api/v1/stock-adjustments", """
                {"productId": %d, "quantity": 4, "reason": "Physical count"}
                """.formatted(productId), token)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultingStock").value(4));
        getJson("/api/v1/inventory-movements?productId=" + productId, token).andExpect(status().isOk());
    }

    @Test
    void supervisorSeesAndCancelsAnotherSellersSale() throws Exception {
        Long productId = createPricedProduct("10.00", "0");
        addStock(productId, 2);
        Long saleId = readId(sell("""
                {"paymentMethod": "CASH", "items": [{"productId": %d, "quantity": 1}]}
                """.formatted(productId), sellerToken()).andExpect(status().isCreated()));
        String token = supervisorToken();

        getJson("/api/v1/sales/" + saleId, token).andExpect(status().isOk());
        postJson("/api/v1/sales/" + saleId + "/cancellation", """
                {"reason": "Wrong product"}
                """, token)
                .andExpect(status().isOk());
        getJson("/api/v1/products/" + productId, token).andExpect(jsonPath("$.stock").value(2));
    }

    @Test
    void supervisorCantManageUserAccounts() throws Exception {
        String token = supervisorToken();

        getJson("/api/v1/users", token).andExpect(status().isForbidden());
        postJson("/api/v1/users", """
                {"name": "Promoted", "email": "promoted-%s@test.com", "password": "%s", "role": "ADMIN"}
                """.formatted(UUID.randomUUID(), DEFAULT_PASSWORD), token)
                .andExpect(status().isForbidden());
    }

    private String supervisorToken() throws Exception {
        return loginOk(createUser("SUPERVISOR").email(), DEFAULT_PASSWORD).accessToken();
    }
}

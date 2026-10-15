package com.irvin.inventory.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvin.inventory.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

class OpenApiConfigIT extends AbstractIntegrationTest {

    @Test
    void apiDocsArePublicAndDescribeBearerAuth() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/v1/users'].get").exists())
                // Login is public: it overrides the global bearer requirement with an empty one.
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security").isEmpty());
    }

    // Nested records share simple names ("Item"); without explicit names springdoc merges them and
    // generated clients get the request item where the sale's lines are.
    @Test
    void nestedItemSchemasKeepTheirOwnShape() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/sales'].post.requestBody.content['application/json'].schema.$ref")
                        .value("#/components/schemas/CreateSaleRequest"))
                .andExpect(jsonPath("$.components.schemas.CreateSaleRequest.properties.items.items.$ref")
                        .value("#/components/schemas/CreateSaleItem"))
                .andExpect(jsonPath("$.components.schemas.SaleResponse.properties.items.items.$ref")
                        .value("#/components/schemas/SaleItemResponse"))
                .andExpect(jsonPath("$.components.schemas.SaleItemResponse.properties.unitPrice").exists())
                .andExpect(jsonPath("$.components.schemas.PurchaseResponse.properties.items.items.$ref")
                        .value("#/components/schemas/PurchaseItemResponse"))
                .andExpect(jsonPath("$.components.schemas.Item").doesNotExist());
    }

    @Test
    void swaggerUiIsPublic() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}

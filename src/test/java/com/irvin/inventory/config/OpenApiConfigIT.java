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

    @Test
    void swaggerUiIsPublic() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}

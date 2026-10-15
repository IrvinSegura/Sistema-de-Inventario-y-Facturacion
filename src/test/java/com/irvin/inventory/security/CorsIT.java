package com.irvin.inventory.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvin.inventory.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

// The test profile uses the default origin, the Vite dev server.
class CorsIT extends AbstractIntegrationTest {

    private static final String WEB_CLIENT = "http://localhost:5173";

    @Test
    void preflightFromTheWebClientIsAllowedWithTheCheckoutHeaders() throws Exception {
        mockMvc.perform(options("/api/v1/sales")
                        .header(HttpHeaders.ORIGIN, WEB_CLIENT)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization, content-type, idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, WEB_CLIENT))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    void preflightFromAnotherOriginOrWithAnUnlistedMethodIsRejected() throws Exception {
        mockMvc.perform(options("/api/v1/sales")
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
        mockMvc.perform(options("/api/v1/sales")
                        .header(HttpHeaders.ORIGIN, WEB_CLIENT)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE"))
                .andExpect(status().isForbidden());
    }

    @Test
    void responsesToTheWebClientCarryTheCorsHeaders() throws Exception {
        mockMvc.perform(withToken(get("/api/v1/categories"), adminAccessToken())
                        .header(HttpHeaders.ORIGIN, WEB_CLIENT))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, WEB_CLIENT))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Location, Retry-After"));
    }
}

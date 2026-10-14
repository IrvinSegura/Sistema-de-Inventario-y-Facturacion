package com.irvin.inventory.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvin.inventory.AbstractIntegrationTest;
import com.irvin.inventory.security.JwtProperties;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AuthControllerIT extends AbstractIntegrationTest {

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void loginReturnsBearerTokens() throws Exception {
        login(ADMIN_EMAIL, ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(jwtProperties.accessTokenTtl().toSeconds()))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void loginIsCaseInsensitiveOnEmail() throws Exception {
        login("  ADMIN@Test.com ", ADMIN_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameResponse() throws Exception {
        login(ADMIN_EMAIL, "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid credentials"));
        login("nobody@test.com", "whatever-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid credentials"));
    }

    @Test
    void protectedEndpointRequiresValidToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
        getJson("/api/v1/users/me", "invalid").andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesTokenAndReuseRevokesAllSessions() throws Exception {
        Tokens first = loginOk(createSeller().email(), DEFAULT_PASSWORD);

        String body = refresh(first.refreshToken())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String second = JsonPath.read(body, "$.refreshToken");

        // Reusing a rotated token is treated as theft: it fails and revokes the newer token too.
        refresh(first.refreshToken()).andExpect(status().isUnauthorized());
        refresh(second).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesOnlyThatSession() throws Exception {
        String email = createSeller().email();
        Tokens sessionA = loginOk(email, DEFAULT_PASSWORD);
        Tokens sessionB = loginOk(email, DEFAULT_PASSWORD);

        logout(sessionA.refreshToken()).andExpect(status().isNoContent());

        // A logged-out token is not a stolen one: reusing it must not kill other sessions.
        refresh(sessionA.refreshToken()).andExpect(status().isUnauthorized());
        refresh(sessionB.refreshToken()).andExpect(status().isOk());
    }

    @Test
    void logoutWithUnknownTokenStillReturnsNoContent() throws Exception {
        logout("unknown-token").andExpect(status().isNoContent());
    }

    @Test
    void deactivatedUserCannotRefresh() throws Exception {
        CreatedUser seller = createSeller();
        Tokens tokens = loginOk(seller.email(), DEFAULT_PASSWORD);

        putJson("/api/v1/users/" + seller.id(), """
                {"name": "Test User", "role": "SELLER", "active": false}
                """, adminAccessToken())
                .andExpect(status().isOk());

        refresh(tokens.refreshToken()).andExpect(status().isUnauthorized());
        login(seller.email(), DEFAULT_PASSWORD).andExpect(status().isUnauthorized());
    }
}

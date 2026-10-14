package com.irvin.inventory;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// Shared context and database for all integration tests: create unique data per test, and
// never leave an extra active ADMIN behind (the last-admin rule test relies on it).
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    // Created on startup by AdminInitializer from application-test.yaml.
    protected static final String ADMIN_EMAIL = "admin@test.com";
    protected static final String ADMIN_PASSWORD = "admin-password";
    protected static final String DEFAULT_PASSWORD = "user-password";

    @Autowired
    protected MockMvc mockMvc;

    protected ResultActions getJson(String url, String accessToken) throws Exception {
        return mockMvc.perform(withToken(get(url), accessToken));
    }

    protected ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions postJson(String url, String json, String accessToken) throws Exception {
        return mockMvc.perform(withToken(post(url), accessToken).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions putJson(String url, String json, String accessToken) throws Exception {
        return mockMvc.perform(withToken(put(url), accessToken).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions login(String email, String password) throws Exception {
        return postJson("/api/v1/auth/login", """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password));
    }

    protected Tokens loginOk(String email, String password) throws Exception {
        String body = login(email, password)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Tokens(JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken"));
    }

    protected ResultActions refresh(String refreshToken) throws Exception {
        return postJson("/api/v1/auth/refresh", refreshBody(refreshToken));
    }

    protected ResultActions logout(String refreshToken) throws Exception {
        return postJson("/api/v1/auth/logout", refreshBody(refreshToken));
    }

    protected String adminAccessToken() throws Exception {
        return loginOk(ADMIN_EMAIL, ADMIN_PASSWORD).accessToken();
    }

    // Unique email per call so tests sharing the database don't collide.
    protected CreatedUser createUser(String role) throws Exception {
        String email = role.toLowerCase() + "-" + UUID.randomUUID() + "@test.com";
        String body = postJson("/api/v1/users", """
                {"name": "Test User", "email": "%s", "password": "%s", "role": "%s"}
                """.formatted(email, DEFAULT_PASSWORD, role), adminAccessToken())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new CreatedUser(((Number) JsonPath.read(body, "$.id")).longValue(), email);
    }

    protected CreatedUser createSeller() throws Exception {
        return createUser("SELLER");
    }

    private static MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static String refreshBody(String refreshToken) {
        return """
                {"refreshToken": "%s"}
                """.formatted(refreshToken);
    }

    protected record Tokens(String accessToken, String refreshToken) {
    }

    protected record CreatedUser(Long id, String email) {
    }
}

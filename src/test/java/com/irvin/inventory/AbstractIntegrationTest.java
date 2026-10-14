package com.irvin.inventory;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
        Long id = readId(postJson("/api/v1/users", """
                {"name": "Test User", "email": "%s", "password": "%s", "role": "%s"}
                """.formatted(email, DEFAULT_PASSWORD, role), adminAccessToken())
                .andExpect(status().isCreated()));
        return new CreatedUser(id, email);
    }

    protected CreatedUser createSeller() throws Exception {
        return createUser("SELLER");
    }

    // Seeded by V2 (Pantry, Dairy, Bakery, Beverages, Snacks, Cleaning, Personal Care).
    protected Long categoryId(String name) throws Exception {
        String body = getJson("/api/v1/categories?includeInactive=true", adminAccessToken())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(body, "$[?(@.name == '" + name + "')].id");
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("No category named " + name);
        }
        return ids.getFirst().longValue();
    }

    protected Long createCategory(String name) throws Exception {
        return readId(postJson("/api/v1/categories", """
                {"name": "%s", "defaultTaxRate": 0}
                """.formatted(name), adminAccessToken())
                .andExpect(status().isCreated()));
    }

    protected Long createProduct(Long categoryId, int minStock) throws Exception {
        return createProduct("Test product", categoryId, minStock);
    }

    // Unique SKU per call.
    protected Long createProduct(String name, Long categoryId, int minStock) throws Exception {
        return readId(postJson("/api/v1/products", """
                {"sku": "%s", "name": "%s", "categoryId": %d, "salePrice": 10.00, "minStock": %d}
                """.formatted(uniqueSku(), name, categoryId, minStock), adminAccessToken())
                .andExpect(status().isCreated()));
    }

    protected Long createSupplier() throws Exception {
        return readId(postJson("/api/v1/suppliers", """
                {"name": "Supplier %s"}
                """.formatted(UUID.randomUUID()), adminAccessToken())
                .andExpect(status().isCreated()));
    }

    protected static Long readId(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    protected static String uniqueSku() {
        return "SKU-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    protected ResultActions postJson(String url, String json, String accessToken, UUID idempotencyKey)
            throws Exception {
        return mockMvc.perform(withToken(post(url), accessToken)
                .header("Idempotency-Key", idempotencyKey.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    protected static MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    // Runs the tasks in parallel, released at the same moment to maximize contention.
    // Prepare tokens and data before calling: slow work inside a task spreads the requests out.
    protected static <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(tasks.size())) {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        }
    }

    protected static int httpStatus(ResultActions result) {
        return result.andReturn().getResponse().getStatus();
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

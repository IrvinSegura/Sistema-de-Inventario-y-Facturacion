package com.irvin.inventory;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

import com.irvin.inventory.config.StoreProperties;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// Shared context and database for all integration tests: create unique data per test, and
// never leave an extra active ADMIN behind (the last-admin rule test relies on it).
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestPasswordResetConfiguration.class,
        TestClientAddressConfiguration.class})
public abstract class AbstractIntegrationTest {

    // Created on startup by AdminInitializer from application-test.yaml.
    protected static final String ADMIN_EMAIL = "admin@test.com";
    protected static final String ADMIN_PASSWORD = "admin-password";
    protected static final String DEFAULT_PASSWORD = "user-password";
    protected static final String CASH_DRAWERS = "/api/v1/cash-drawers";

    // Sales need an open cash drawer; the admin's is shared by every test, so none may close it.
    // Its local day: a run that crosses midnight closes it and opens the new day's.
    private static final AtomicReference<LocalDate> ADMIN_DRAWER_DAY = new AtomicReference<>();

    @Autowired
    protected MockMvc mockMvc;

    // Direct SQL for assertions and for state the API can't set (e.g. past dates).
    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    private StoreProperties storeProperties;

    @BeforeEach
    void openAdminDrawer() throws Exception {
        LocalDate today = LocalDate.now(storeProperties.timeZone());
        if (today.equals(ADMIN_DRAWER_DAY.get())) {
            return;
        }
        jdbcTemplate.update("""
                UPDATE cash_drawer
                SET closed_at = now(), closed_by = user_id, expected_cash = opening_float, counted_cash = opening_float
                WHERE closed_at IS NULL AND user_id = (SELECT id FROM app_user WHERE email = ?)""", ADMIN_EMAIL);
        openDrawer(adminAccessToken(), "0");
        ADMIN_DRAWER_DAY.set(today);
    }

    // Opens today's drawer of the token's user.
    protected Long openDrawer(String accessToken, String openingFloat) throws Exception {
        return readId(postJson(CASH_DRAWERS, """
                {"openingFloat": %s}
                """.formatted(openingFloat), accessToken)
                .andExpect(status().isCreated()));
    }

    protected int stock(Long productId) {
        return jdbcTemplate.queryForObject("SELECT stock FROM product WHERE id = ?", Integer.class, productId);
    }

    // Resends the product's current data with active=false, so tests don't depend on how it was created.
    protected void deactivateProduct(Long productId) throws Exception {
        Map<String, Object> product = jdbcTemplate.queryForMap("""
                SELECT sku, name, category_id, sale_price, tax_rate, min_stock, version FROM product WHERE id = ?""",
                productId);
        putJson("/api/v1/products/" + productId, """
                {"sku": "%s", "name": "%s", "categoryId": %s, "salePrice": %s, "taxRate": %s,
                 "minStock": %s, "active": false, "version": %s}
                """.formatted(product.get("sku"), product.get("name"), product.get("category_id"),
                        product.get("sale_price"), product.get("tax_rate"), product.get("min_stock"),
                        product.get("version")), adminAccessToken())
                .andExpect(status().isOk());
    }

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

    // Company tax ID (RFC): 3 letters + YYMMDD + 3 chars; random letters and suffix keep it unique across tests.
    protected static String randomCompanyTaxId() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder letters = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            letters.append((char) ('A' + random.nextInt(26)));
        }
        return letters + "990101" + random.nextInt(100, 1000);
    }

    // A new seller with today's drawer open (zero opening float), ready to sell.
    protected String sellerToken() throws Exception {
        return sellerToken(createSeller());
    }

    // Logs the user in and opens today's drawer (zero opening float).
    protected String sellerToken(CreatedUser seller) throws Exception {
        String token = loginOk(seller.email(), DEFAULT_PASSWORD).accessToken();
        openDrawer(token, "0");
        return token;
    }

    // Unique SKU per call, in the seeded Pantry category.
    protected Long createPricedProduct(String salePrice, String taxRate) throws Exception {
        return readId(postJson("/api/v1/products", """
                {"sku": "%s", "name": "Sale product", "categoryId": %d, "salePrice": %s, "taxRate": %s, "minStock": 0}
                """.formatted(uniqueSku(), categoryId("Pantry"), salePrice, taxRate), adminAccessToken())
                .andExpect(status().isCreated()));
    }

    // Purchases are the only way to add stock.
    protected void addStock(Long productId, int quantity) throws Exception {
        postJson("/api/v1/purchases", """
                {"supplierId": %d, "items": [{"productId": %d, "quantity": %d, "unitCost": 1}]}
                """.formatted(createSupplier(), productId, quantity), adminAccessToken())
                .andExpect(status().isCreated());
    }

    // Sales require an Idempotency-Key; a fresh one per call records a new sale.
    protected ResultActions sell(String body, String token) throws Exception {
        return postJson("/api/v1/sales", body, token, UUID.randomUUID());
    }

    protected Long createCustomer() throws Exception {
        return readId(postJson("/api/v1/customers", """
                {"name": "Customer %s"}
                """.formatted(UUID.randomUUID()), adminAccessToken())
                .andExpect(status().isCreated()));
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

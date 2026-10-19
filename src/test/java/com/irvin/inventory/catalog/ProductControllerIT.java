package com.irvin.inventory.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.irvin.inventory.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

class ProductControllerIT extends AbstractIntegrationTest {

    @Test
    void createUsesCategoryTaxRateAndStartsWithZeroStock() throws Exception {
        String sku = uniqueSku();

        postJson("/api/v1/products", productBody(sku, null, "Soda 600ml", categoryId("Beverages"), null), adminAccessToken())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value(sku))
                .andExpect(jsonPath("$.category.name").value("Beverages"))
                .andExpect(jsonPath("$.taxRate").value(0.16))
                .andExpect(jsonPath("$.stock").value(0))
                .andExpect(jsonPath("$.lowStock").value(true))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void explicitTaxRateOverridesCategoryDefault() throws Exception {
        // E.g. chocolate in Pantry pays 16% although basic food pays 0%.
        postJson("/api/v1/products", productBody(uniqueSku(), null, "Chocolate", categoryId("Pantry"), "0.16"),
                adminAccessToken())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taxRate").value(0.16));
    }

    @Test
    void skuIsNormalizedAndSkuOrBarcodeDuplicatesConflict() throws Exception {
        String adminToken = adminAccessToken();
        String sku = uniqueSku();
        String barcode = randomBarcode();
        Long pantry = categoryId("Pantry");

        postJson("/api/v1/products", productBody(" " + sku.toLowerCase() + " ", barcode, "Rice", pantry, null), adminToken)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value(sku));
        postJson("/api/v1/products", productBody(sku, null, "Rice copy", pantry, null), adminToken)
                .andExpect(status().isConflict());
        postJson("/api/v1/products", productBody(uniqueSku(), barcode, "Rice copy", pantry, null), adminToken)
                .andExpect(status().isConflict());
    }

    @Test
    void invalidFieldsReturnFieldErrors() throws Exception {
        postJson("/api/v1/products", """
                {"sku": "", "barcode": "ABC", "name": "X", "categoryId": 1, "salePrice": -1,
                 "taxRate": 1.5, "minStock": -1, "imageUrl": "https://x.com/a.png\\"onerror=\\"alert(1)"}
                """, adminAccessToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.sku").exists())
                .andExpect(jsonPath("$.errors.barcode").exists())
                .andExpect(jsonPath("$.errors.salePrice").exists())
                .andExpect(jsonPath("$.errors.taxRate").exists())
                .andExpect(jsonPath("$.errors.minStock").exists())
                .andExpect(jsonPath("$.errors.imageUrl").exists());
    }

    @Test
    void unknownOrInactiveCategoryIsRejected() throws Exception {
        String adminToken = adminAccessToken();
        Long inactiveId = createInactiveCategory();

        postJson("/api/v1/products", productBody(uniqueSku(), null, "X", 999999L, null), adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Category not found"));
        postJson("/api/v1/products", productBody(uniqueSku(), null, "X", inactiveId, null), adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Category is inactive"));
    }

    @Test
    void productCannotBeReactivatedInsideAnInactiveCategory() throws Exception {
        String adminToken = adminAccessToken();
        String name = "Closing " + UUID.randomUUID();
        Long categoryId = createCategory(name);
        Long productId = createProduct(categoryId, 0);
        String sku = uniqueSku();

        putJson("/api/v1/products/" + productId, updateBody(sku, categoryId, false, 0), adminToken)
                .andExpect(status().isOk());
        putJson("/api/v1/categories/" + categoryId, """
                {"name": "%s", "defaultTaxRate": 0, "active": false, "version": 0}
                """.formatted(name), adminToken)
                .andExpect(status().isOk());

        // Same category, only "active" changes: the category must still be checked.
        putJson("/api/v1/products/" + productId, updateBody(sku, categoryId, true, 1), adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Category is inactive"));
    }

    @Test
    void sellerCanReadButNotWrite() throws Exception {
        String barcode = randomBarcode();
        String adminToken = adminAccessToken();
        Long dairy = categoryId("Dairy");
        Long id = readId(postJson("/api/v1/products", productBody(uniqueSku(), barcode, "Milk 1L", dairy, null), adminToken)
                .andExpect(status().isCreated()));
        String sellerToken = loginOk(createSeller().email(), DEFAULT_PASSWORD).accessToken();

        getJson("/api/v1/products", sellerToken).andExpect(status().isOk());
        getJson("/api/v1/products/" + id, sellerToken).andExpect(status().isOk());
        getJson("/api/v1/products/barcode/" + barcode, sellerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        postJson("/api/v1/products", productBody(uniqueSku(), null, "X", dairy, null), sellerToken)
                .andExpect(status().isForbidden());
        putJson("/api/v1/products/" + id, updateBody(uniqueSku(), dairy, true, 0), sellerToken)
                .andExpect(status().isForbidden());
    }

    @Test
    void barcodeScanIgnoresInactiveProducts() throws Exception {
        String adminToken = adminAccessToken();
        String barcode = randomBarcode();
        String sku = uniqueSku();
        Long pantry = categoryId("Pantry");
        Long id = readId(postJson("/api/v1/products", productBody(sku, barcode, "Old item", pantry, null), adminToken)
                .andExpect(status().isCreated()));

        putJson("/api/v1/products/" + id, updateBody(sku, barcode, pantry, false, 0), adminToken)
                .andExpect(status().isOk());

        getJson("/api/v1/products/barcode/" + barcode, adminToken).andExpect(status().isNotFound());
    }

    @Test
    void unknownProductOrBarcodeReturnsNotFound() throws Exception {
        String token = adminAccessToken();
        getJson("/api/v1/products/999999", token).andExpect(status().isNotFound());
        getJson("/api/v1/products/barcode/00000000", token).andExpect(status().isNotFound());
        putJson("/api/v1/products/999999", updateBody(uniqueSku(), categoryId("Pantry"), true, 0), token)
                .andExpect(status().isNotFound());
    }

    @Test
    void searchFiltersByTextCategoryAndLowStock() throws Exception {
        String token = adminAccessToken();
        String tag = "Tag" + UUID.randomUUID().toString().substring(0, 8);
        Long snacks = categoryId("Snacks");
        Long wellStocked = createProduct(tag + " chips", snacks, 5);
        Long lowStock = createProduct(tag + " candy", snacks, 5);
        setStock(wellStocked, 50);

        getJson("/api/v1/products?search=" + tag.toLowerCase(), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].name").value(tag + " candy"));
        getJson("/api/v1/products?search=" + tag + "&lowStock=true", token)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(lowStock));
        getJson("/api/v1/products?search=" + tag + "&categoryId=" + categoryId("Dairy"), token)
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void searchSortsByAllowedFieldsOnly() throws Exception {
        String token = adminAccessToken();
        String tag = "Sort" + UUID.randomUUID().toString().substring(0, 8);
        Long snacks = categoryId("Snacks");
        Long few = createProduct(tag + " a", snacks, 5);
        Long many = createProduct(tag + " b", snacks, 5);
        Long some = createProduct(tag + " c", snacks, 5);
        setStock(few, 1);
        setStock(many, 90);
        setStock(some, 40);

        getJson("/api/v1/products?search=" + tag + "&sort=stock,desc", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(many))
                .andExpect(jsonPath("$.content[1].id").value(some))
                .andExpect(jsonPath("$.content[2].id").value(few));
        getJson("/api/v1/products?search=" + tag + "&sort=name,desc", token)
                .andExpect(jsonPath("$.content[0].id").value(some));
        // Only listed fields: nothing else (e.g. internal columns) can drive the order.
        getJson("/api/v1/products?search=" + tag + "&sort=version", token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Unknown sort property: version")));
    }

    @Test
    void searchFindsInactiveProductsByExactBarcode() throws Exception {
        String token = adminAccessToken();
        String barcode = randomBarcode();
        String sku = uniqueSku();
        Long pantry = categoryId("Pantry");
        Long id = readId(postJson("/api/v1/products", productBody(sku, barcode, "Scanned " + sku, pantry, null), token)
                .andExpect(status().isCreated()));
        putJson("/api/v1/products/" + id, updateBody(sku, barcode, pantry, false, 0), token).andExpect(status().isOk());

        getJson("/api/v1/products?active=false&search=" + barcode, token)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id));
        // Part of a barcode doesn't match.
        getJson("/api/v1/products?active=false&search=" + barcode.substring(0, 8), token)
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void searchMatchesLikeWildcardsLiterally() throws Exception {
        String token = adminAccessToken();
        String tag = "Pct" + UUID.randomUUID().toString().substring(0, 8);
        Long pantry = categoryId("Pantry");
        Long literal = createProduct(tag + " 50%", pantry, 0);
        createProduct(tag + " 500", pantry, 0);

        // Unescaped, "50%" would also match "500". MockMvc URL-encodes the space and "%".
        getJson("/api/v1/products?search=" + tag + " 50%", token)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(literal));
    }

    @Test
    void updateKeepsStockAndRejectsStaleVersion() throws Exception {
        String token = adminAccessToken();
        Long pantry = categoryId("Pantry");
        Long id = createProduct(pantry, 0);
        setStock(id, 7);

        String newSku = uniqueSku();
        putJson("/api/v1/products/" + id, updateBody(newSku, pantry, true, 0), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value(newSku))
                .andExpect(jsonPath("$.stock").value(7))
                .andExpect(jsonPath("$.version").value(1));

        // Another admin still holding version 0 must reload instead of overwriting.
        putJson("/api/v1/products/" + id, updateBody(uniqueSku(), pantry, true, 0), token)
                .andExpect(status().isConflict());
    }

    @Test
    void updateRejectsDuplicatesAndInvalidCategory() throws Exception {
        String token = adminAccessToken();
        Long pantry = categoryId("Pantry");
        String takenSku = uniqueSku();
        String takenBarcode = randomBarcode();
        postJson("/api/v1/products", productBody(takenSku, takenBarcode, "Taken", pantry, null), token)
                .andExpect(status().isCreated());
        Long id = createProduct(pantry, 0);

        putJson("/api/v1/products/" + id, updateBody(takenSku, pantry, true, 0), token)
                .andExpect(status().isConflict());
        putJson("/api/v1/products/" + id, updateBody(uniqueSku(), takenBarcode, pantry, true, 0), token)
                .andExpect(status().isConflict());
        putJson("/api/v1/products/" + id, updateBody(uniqueSku(), 999999L, true, 0), token)
                .andExpect(status().isBadRequest());
        putJson("/api/v1/products/" + id, updateBody(uniqueSku(), createInactiveCategory(), true, 0), token)
                .andExpect(status().isBadRequest());
    }

    @Test
    void namesAreUniqueIgnoringCase() throws Exception {
        String token = adminAccessToken();
        Long pantry = categoryId("Pantry");
        String name = "Tag" + UUID.randomUUID().toString().substring(0, 8) + " Agua 1L";
        postJson("/api/v1/products", productBody(uniqueSku(), null, name, pantry, null), token)
                .andExpect(status().isCreated());
        Long other = createProduct(pantry, 0);

        postJson("/api/v1/products", productBody(uniqueSku(), null, name.toUpperCase(), pantry, null), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Name already exists"));
        putJson("/api/v1/products/" + other, """
                {"sku": "%s", "name": "%s", "categoryId": %d, "salePrice": 1, "taxRate": 0, "minStock": 0,
                 "active": true, "version": 0}
                """.formatted(uniqueSku(), name.toLowerCase(), pantry), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Name already exists"));
    }

    @Test
    void skuAndNameRejectCharactersOutsideTheirRules() throws Exception {
        String token = adminAccessToken();
        Long pantry = categoryId("Pantry");

        postJson("/api/v1/products", productBody("SKU 1!", null, "<b>Bold</b>", pantry, null), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.sku").exists())
                .andExpect(jsonPath("$.errors.name").exists());
        // Accents, digits and common punctuation are fine; the SKU is uppercased before the check.
        postJson("/api/v1/products", productBody("ab-" + UUID.randomUUID().toString().substring(0, 6), null,
                "Jabón Zote 400 g (2 pzs) " + UUID.randomUUID().toString().substring(0, 6), pantry, null), token)
                .andExpect(status().isCreated());
    }

    @Test
    void onlyProductsWithoutMovementsCanBeDeleted() throws Exception {
        String token = adminAccessToken();
        Long pantry = categoryId("Pantry");
        Long unused = createProduct(pantry, 0);
        Long adjusted = createProduct(pantry, 0);
        postJson("/api/v1/stock-adjustments", """
                {"productId": %d, "quantity": 3, "reason": "Initial stock"}
                """.formatted(adjusted), token)
                .andExpect(status().isCreated());

        mockMvc.perform(withToken(delete("/api/v1/products/{id}", unused), token)).andExpect(status().isNoContent());
        getJson("/api/v1/products/" + unused, token).andExpect(status().isNotFound());
        mockMvc.perform(withToken(delete("/api/v1/products/{id}", adjusted), token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(ProductService.IN_USE_MESSAGE));
        mockMvc.perform(withToken(delete("/api/v1/products/{id}", 999_999_999L), token)).andExpect(status().isNotFound());
        mockMvc.perform(withToken(delete("/api/v1/products/{id}", adjusted), sellerToken())).andExpect(status().isForbidden());
    }

    @Test
    void productsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/products")).andExpect(status().isUnauthorized());
    }

    private Long createInactiveCategory() throws Exception {
        String name = "Closed " + UUID.randomUUID();
        Long id = createCategory(name);
        putJson("/api/v1/categories/" + id, """
                {"name": "%s", "defaultTaxRate": 0, "active": false, "version": 0}
                """.formatted(name), adminAccessToken())
                .andExpect(status().isOk());
        return id;
    }

    // Catalog tests set stock directly to stay independent of the purchasing and stock modules.
    private void setStock(Long productId, int stock) {
        jdbcTemplate.update("UPDATE product SET stock = ? WHERE id = ?", stock, productId);
    }

    private static String randomBarcode() {
        return String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000_000_000L, 9_999_999_999_999L));
    }

    private static String productBody(String sku, String barcode, String name, Long categoryId, String taxRate) {
        return """
                {"sku": "%s", "barcode": %s, "name": "%s", "categoryId": %d, "salePrice": 17.24,
                 "taxRate": %s, "minStock": 5}
                """.formatted(sku, jsonString(barcode), name, categoryId, taxRate);
    }

    private static String updateBody(String sku, Long categoryId, boolean active, long version) {
        return updateBody(sku, null, categoryId, active, version);
    }

    private static String updateBody(String sku, String barcode, Long categoryId, boolean active, long version) {
        // Named after the SKU: names are unique, and each update sends a fresh SKU.
        return """
                {"sku": "%s", "barcode": %s, "name": "Updated %s", "categoryId": %d, "salePrice": 12.50,
                 "taxRate": 0, "minStock": 0, "active": %s, "version": %d}
                """.formatted(sku, jsonString(barcode), sku, categoryId, active, version);
    }

    private static String jsonString(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}

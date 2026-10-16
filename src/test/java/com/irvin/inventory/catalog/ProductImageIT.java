package com.irvin.inventory.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import com.irvin.inventory.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

// Runs with LocalImageStorage (no Cloudinary settings in tests).
class ProductImageIT extends AbstractIntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};
    private static final String LOCAL_BASE_URL = "http://localhost:8080";

    @Test
    void uploadStoresTheImageServesItPubliclyAndBumpsTheVersion() throws Exception {
        Long productId = createProduct(categoryId("Snacks"), 1);

        String body = uploadImage(productId, "photo.png", PNG, adminAccessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andReturn().getResponse().getContentAsString();
        String imageUrl = JsonPath.read(body, "$.imageUrl");

        assertThat(imageUrl).startsWith(LOCAL_BASE_URL + "/images/products/" + productId + "-").endsWith(".png");
        // No token: <img> tags can't send one.
        mockMvc.perform(get(imageUrl.substring(LOCAL_BASE_URL.length())))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PNG));
    }

    @Test
    void extensionComesFromTheContentNotTheFileName() throws Exception {
        Long productId = createProduct(categoryId("Snacks"), 1);

        uploadImage(productId, "photo.png", JPEG, adminAccessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value(endsWith(".jpg")));
    }

    @Test
    void replacingOrRemovingDeletesThePreviousFile() throws Exception {
        String adminToken = adminAccessToken();
        Long productId = createProduct(categoryId("Snacks"), 1);
        String first = imageUrl(uploadImage(productId, "a.png", PNG, adminToken).andExpect(status().isOk()));
        String second = imageUrl(uploadImage(productId, "b.jpg", JPEG, adminToken).andExpect(status().isOk()));

        mockMvc.perform(get(first.substring(LOCAL_BASE_URL.length()))).andExpect(status().isNotFound());

        mockMvc.perform(withToken(delete("/api/v1/products/{id}/image", productId), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").doesNotExist());
        mockMvc.perform(get(second.substring(LOCAL_BASE_URL.length()))).andExpect(status().isNotFound());
    }

    @Test
    void nonImagesAndEmptyFilesAreRejected() throws Exception {
        String adminToken = adminAccessToken();
        Long productId = createProduct(categoryId("Snacks"), 1);
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);

        uploadImage(productId, "logo.png", svg, adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Image must be PNG, JPEG, WebP or GIF"));
        uploadImage(productId, "empty.png", new byte[0], adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Image file is required"));
    }

    @Test
    void unknownProductIsNotFoundAndSellersCantUpload() throws Exception {
        Long productId = createProduct(categoryId("Snacks"), 1);

        uploadImage(999_999_999L, "a.png", PNG, adminAccessToken()).andExpect(status().isNotFound());
        uploadImage(productId, "a.png", PNG, sellerToken()).andExpect(status().isForbidden());
        mockMvc.perform(withToken(delete("/api/v1/products/{id}/image", productId), sellerToken()))
                .andExpect(status().isForbidden());
    }

    private ResultActions uploadImage(Long productId, String fileName, byte[] content, String token) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", fileName, "application/octet-stream", content);
        // Multipart builders aren't MockHttpServletRequestBuilders, so withToken doesn't apply.
        return mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/products/{id}/image", productId).file(file)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private static String imageUrl(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.imageUrl");
    }
}

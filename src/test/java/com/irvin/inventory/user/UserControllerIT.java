package com.irvin.inventory.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.irvin.inventory.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

class UserControllerIT extends AbstractIntegrationTest {

    @Test
    void adminCreatesUserWithoutExposingPassword() throws Exception {
        String email = "new-" + UUID.randomUUID() + "@test.com";

        postJson("/api/v1/users", sellerBody(email.toUpperCase(), DEFAULT_PASSWORD), adminAccessToken())
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("SELLER"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void duplicateEmailReturnsConflict() throws Exception {
        String email = createSeller().email();

        postJson("/api/v1/users", sellerBody(email.toUpperCase(), DEFAULT_PASSWORD), adminAccessToken())
                .andExpect(status().isConflict());
    }

    @Test
    void invalidRequestReturnsFieldErrors() throws Exception {
        postJson("/api/v1/users", """
                {"name": "", "email": "not-an-email", "password": "short", "role": "SELLER"}
                """, adminAccessToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void passwordOver72BytesIsRejectedEvenWithFewCharacters() throws Exception {
        String fortyEnyes = "ñ".repeat(40); // 40 characters, 80 bytes in UTF-8

        postJson("/api/v1/users", sellerBody("x-" + UUID.randomUUID() + "@test.com", fortyEnyes), adminAccessToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void listIsPaged() throws Exception {
        getJson("/api/v1/users?size=1", adminAccessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page.size").value(1));
    }

    @Test
    void sellerCannotManageUsersButCanReadOwnProfile() throws Exception {
        CreatedUser seller = createSeller();
        String token = loginOk(seller.email(), DEFAULT_PASSWORD).accessToken();

        getJson("/api/v1/users", token).andExpect(status().isForbidden());
        postJson("/api/v1/users", sellerBody("x-" + UUID.randomUUID() + "@test.com", DEFAULT_PASSWORD), token)
                .andExpect(status().isForbidden());
        putJson("/api/v1/users/" + seller.id(), updateBody("ADMIN", true), token)
                .andExpect(status().isForbidden());
        getJson("/api/v1/users/me", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(seller.email()));
    }

    @Test
    void roleChangeIsPersistedAndRevokesSessions() throws Exception {
        CreatedUser seller = createSeller();
        Tokens tokens = loginOk(seller.email(), DEFAULT_PASSWORD);
        String adminToken = adminAccessToken();

        putJson("/api/v1/users/" + seller.id(), updateBody("ADMIN", true), adminToken)
                .andExpect(status().isOk());
        getJson("/api/v1/users/" + seller.id(), adminToken)
                .andExpect(jsonPath("$.role").value("ADMIN"));
        refresh(tokens.refreshToken()).andExpect(status().isUnauthorized());

        // Restore: no extra active admin may be left behind (see AbstractIntegrationTest).
        putJson("/api/v1/users/" + seller.id(), updateBody("SELLER", false), adminToken)
                .andExpect(status().isOk());
    }

    @Test
    void updateValidatesInputAndUnknownIds() throws Exception {
        String adminToken = adminAccessToken();
        CreatedUser seller = createSeller();

        putJson("/api/v1/users/" + seller.id(), """
                {"name": "", "role": null, "active": null}
                """, adminToken)
                .andExpect(status().isBadRequest());
        putJson("/api/v1/users/999999", updateBody("SELLER", true), adminToken)
                .andExpect(status().isNotFound());
        getJson("/api/v1/users/999999", adminToken).andExpect(status().isNotFound());
    }

    @Test
    void changePasswordRevokesSessions() throws Exception {
        String email = createSeller().email();
        Tokens tokens = loginOk(email, DEFAULT_PASSWORD);

        putJson("/api/v1/users/me/password", """
                {"currentPassword": "%s", "newPassword": "new-password-123"}
                """.formatted(DEFAULT_PASSWORD), tokens.accessToken())
                .andExpect(status().isNoContent());

        refresh(tokens.refreshToken()).andExpect(status().isUnauthorized());
        login(email, DEFAULT_PASSWORD).andExpect(status().isUnauthorized());
        login(email, "new-password-123").andExpect(status().isOk());
    }

    @Test
    void wrongCurrentPasswordIsBadRequestNotUnauthorized() throws Exception {
        String token = loginOk(createSeller().email(), DEFAULT_PASSWORD).accessToken();

        // 401 would make clients think their session expired.
        putJson("/api/v1/users/me/password", """
                {"currentPassword": "wrong-password", "newPassword": "new-password-123"}
                """, token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Current password is incorrect"));
    }

    @Test
    void lastActiveAdminCannotLoseAdminAccess() throws Exception {
        String adminToken = adminAccessToken();
        Long seededAdminId = currentUserId(adminToken);
        CreatedUser secondAdmin = createUser("ADMIN");

        // With two active admins, demoting one is allowed...
        putJson("/api/v1/users/" + secondAdmin.id(), updateBody("SELLER", false), adminToken)
                .andExpect(status().isOk());

        // ...but the remaining one can be neither demoted nor deactivated.
        putJson("/api/v1/users/" + seededAdminId, updateBody("SELLER", true), adminToken)
                .andExpect(status().isConflict());
        putJson("/api/v1/users/" + seededAdminId, updateBody("ADMIN", false), adminToken)
                .andExpect(status().isConflict());
    }

    @Test
    void concurrentMutualDemotionKeepsOneActiveAdmin() throws Exception {
        String seededToken = adminAccessToken();
        Long seededAdminId = currentUserId(seededToken);
        CreatedUser secondAdmin = createUser("ADMIN");
        String secondToken = loginOk(secondAdmin.email(), DEFAULT_PASSWORD).accessToken();

        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(() -> demoteWhenReleased(start, secondAdmin.id(), seededToken));
            Future<Integer> second = executor.submit(() -> demoteWhenReleased(start, seededAdminId, secondToken));
            start.countDown();

            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(200, 409);
        } finally {
            // Both access tokens still carry the ADMIN claim, so either can restore the initial state.
            putJson("/api/v1/users/" + seededAdminId, updateBody("ADMIN", true), secondToken);
            putJson("/api/v1/users/" + secondAdmin.id(), updateBody("SELLER", false), seededToken)
                    .andExpect(status().isOk());
        }
    }

    private int demoteWhenReleased(CountDownLatch start, Long targetId, String token) throws Exception {
        start.await();
        return putJson("/api/v1/users/" + targetId, updateBody("SELLER", true), token)
                .andReturn().getResponse().getStatus();
    }

    private Long currentUserId(String accessToken) throws Exception {
        String body = getJson("/api/v1/users/me", accessToken).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static String sellerBody(String email, String password) {
        return """
                {"name": "New Seller", "email": "%s", "password": "%s", "role": "SELLER"}
                """.formatted(email, password);
    }

    private static String updateBody(String role, boolean active) {
        return """
                {"name": "Test User", "role": "%s", "active": %s}
                """.formatted(role, active);
    }
}

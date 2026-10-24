package com.irvin.inventory.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.irvin.inventory.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

// An admin changing a user's sign-in email and resetting their password to a temporary one.
class UserCredentialsIT extends AbstractIntegrationTest {

    private static final String TEMPORARY_PASSWORD = "temporary-pass-1";
    private static final String NEW_PASSWORD = "brand-new-pass-1";

    @Test
    void adminChangesTheSignInEmail() throws Exception {
        String token = adminAccessToken();
        CreatedUser seller = createSeller();
        String newEmail = "renamed-" + UUID.randomUUID() + "@test.com";

        // Normalized like on creation.
        putJson(userUrl(seller.id()), updateBody(newEmail.toUpperCase()), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(newEmail));

        login(seller.email(), DEFAULT_PASSWORD).andExpect(status().isUnauthorized());
        login(newEmail, DEFAULT_PASSWORD).andExpect(status().isOk());
        // Without an email the current one stays.
        putJson(userUrl(seller.id()), updateBody(null), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(newEmail));
    }

    @Test
    void theOtherChangesOfTheSameSaveAreKeptToo() throws Exception {
        String token = adminAccessToken();
        CreatedUser seller = createSeller();
        String newEmail = "moved-" + UUID.randomUUID() + "@test.com";

        putJson(userUrl(seller.id()), """
                {"name": "Renamed User", "email": "%s", "role": "SUPERVISOR", "active": false}
                """.formatted(newEmail), token).andExpect(status().isOk());

        // Read back from the database, not from the update's response.
        getJson(userUrl(seller.id()), token)
                .andExpect(jsonPath("$.email").value(newEmail))
                .andExpect(jsonPath("$.name").value("Renamed User"))
                .andExpect(jsonPath("$.role").value("SUPERVISOR"))
                .andExpect(jsonPath("$.active").value(false));
        login(newEmail, DEFAULT_PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void anEmailInUseOrInvalidIsRejected() throws Exception {
        String token = adminAccessToken();
        CreatedUser seller = createSeller();
        CreatedUser other = createSeller();

        putJson(userUrl(seller.id()), updateBody(other.email()), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Email is already registered"));
        putJson(userUrl(seller.id()), updateBody("not-an-email"), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
        // Its own email isn't "in use".
        putJson(userUrl(seller.id()), updateBody(seller.email()), token).andExpect(status().isOk());
    }

    @Test
    void temporaryPasswordMustBeChangedBeforeAnythingElse() throws Exception {
        CreatedUser seller = createSeller();
        Tokens oldSession = loginOk(seller.email(), DEFAULT_PASSWORD);

        resetPassword(seller.id(), TEMPORARY_PASSWORD, adminAccessToken()).andExpect(status().isNoContent());

        refresh(oldSession.refreshToken()).andExpect(status().isUnauthorized());
        login(seller.email(), DEFAULT_PASSWORD).andExpect(status().isUnauthorized());
        Tokens temporary = loginOk(seller.email(), TEMPORARY_PASSWORD);
        getJson("/api/v1/users/me", temporary.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired").value(true));
        getJson("/api/v1/products", temporary.accessToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Password change required"));
        // A refreshed session still carries the requirement.
        String refreshed = readAccessToken(refresh(temporary.refreshToken()).andExpect(status().isOk()));
        getJson("/api/v1/products", refreshed).andExpect(status().isForbidden());

        // Keeping the temporary one (which the admin knows) doesn't count as changing it.
        changeOwnPassword(temporary.accessToken(), TEMPORARY_PASSWORD, TEMPORARY_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("New password must differ from the current one"));
        changeOwnPassword(temporary.accessToken(), TEMPORARY_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        Tokens changed = loginOk(seller.email(), NEW_PASSWORD);
        getJson("/api/v1/users/me", changed.accessToken())
                .andExpect(jsonPath("$.passwordChangeRequired").value(false));
        getJson("/api/v1/products", changed.accessToken()).andExpect(status().isOk());
    }

    @Test
    void theUserCanSignOutBeforeChangingIt() throws Exception {
        CreatedUser seller = createSeller();
        resetPassword(seller.id(), TEMPORARY_PASSWORD, adminAccessToken()).andExpect(status().isNoContent());
        Tokens temporary = loginOk(seller.email(), TEMPORARY_PASSWORD);

        // Clients may send the bearer token to the public auth endpoints too.
        mockMvc.perform(withToken(post("/api/v1/auth/logout"), temporary.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}
                                """.formatted(temporary.refreshToken())))
                .andExpect(status().isNoContent());
        refresh(temporary.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void resetRulesAndPermissions() throws Exception {
        String adminToken = adminAccessToken();
        CreatedUser seller = createSeller();
        String sellerToken = loginOk(seller.email(), DEFAULT_PASSWORD).accessToken();
        String supervisorToken = loginOk(createUser("SUPERVISOR").email(), DEFAULT_PASSWORD).accessToken();
        Long adminId = readId(getJson("/api/v1/users/me", adminToken));

        resetPassword(seller.id(), TEMPORARY_PASSWORD, sellerToken).andExpect(status().isForbidden());
        resetPassword(seller.id(), TEMPORARY_PASSWORD, supervisorToken).andExpect(status().isForbidden());
        resetPassword(seller.id(), "short", adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.temporaryPassword").exists());
        resetPassword(999_999L, TEMPORARY_PASSWORD, adminToken).andExpect(status().isNotFound());
        resetPassword(adminId, TEMPORARY_PASSWORD, adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Change your own password from your account"));

        // Nothing changed for the seller.
        login(seller.email(), DEFAULT_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void openSessionsEndAtOnceWhenAnAdminActs() throws Exception {
        String adminToken = adminAccessToken();
        CreatedUser reset = createSeller();
        CreatedUser promoted = createSeller();
        CreatedUser deactivated = createSeller();
        String resetToken = loginOk(reset.email(), DEFAULT_PASSWORD).accessToken();
        String promotedToken = loginOk(promoted.email(), DEFAULT_PASSWORD).accessToken();
        String deactivatedToken = loginOk(deactivated.email(), DEFAULT_PASSWORD).accessToken();

        resetPassword(reset.id(), TEMPORARY_PASSWORD, adminToken).andExpect(status().isNoContent());
        putJson(userUrl(promoted.id()), """
                {"name": "Test User", "role": "SUPERVISOR", "active": true}
                """, adminToken).andExpect(status().isOk());
        putJson(userUrl(deactivated.id()), """
                {"name": "Test User", "role": "SELLER", "active": false}
                """, adminToken).andExpect(status().isOk());

        // Access tokens stop working at once, not when they expire.
        getJson("/api/v1/users/me", resetToken).andExpect(status().isUnauthorized());
        getJson("/api/v1/users/me", promotedToken).andExpect(status().isUnauthorized());
        getJson("/api/v1/users/me", deactivatedToken).andExpect(status().isUnauthorized());
        // Signing in again gives a token with the new role.
        getJson("/api/v1/purchases", loginOk(promoted.email(), DEFAULT_PASSWORD).accessToken())
                .andExpect(status().isOk());
    }

    @Test
    void renamingDoesNotEndSessions() throws Exception {
        CreatedUser seller = createSeller();
        String sellerToken = loginOk(seller.email(), DEFAULT_PASSWORD).accessToken();

        putJson(userUrl(seller.id()), updateBody("kept-" + UUID.randomUUID() + "@test.com"), adminAccessToken())
                .andExpect(status().isOk());

        getJson("/api/v1/users/me", sellerToken).andExpect(status().isOk());
    }

    @Test
    void aStaleBearerTokenDoesNotBlockSigningInAgainOrOut() throws Exception {
        CreatedUser seller = createSeller();
        Tokens old = loginOk(seller.email(), DEFAULT_PASSWORD);
        changeOwnPassword(old.accessToken(), DEFAULT_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        // Clients may still send the token the change just invalidated.
        mockMvc.perform(withToken(post("/api/v1/auth/login"), old.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(seller.email(), NEW_PASSWORD)))
                .andExpect(status().isOk());
        mockMvc.perform(withToken(post("/api/v1/auth/logout"), old.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}
                                """.formatted(old.refreshToken())))
                .andExpect(status().isNoContent());
    }

    @Test
    void changingYourPasswordEndsYourOtherSessionsAtOnce() throws Exception {
        CreatedUser seller = createSeller();
        String otherDevice = loginOk(seller.email(), DEFAULT_PASSWORD).accessToken();
        String thisDevice = loginOk(seller.email(), DEFAULT_PASSWORD).accessToken();

        changeOwnPassword(thisDevice, DEFAULT_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        getJson("/api/v1/users/me", otherDevice).andExpect(status().isUnauthorized());
        getJson("/api/v1/users/me", loginOk(seller.email(), NEW_PASSWORD).accessToken()).andExpect(status().isOk());
    }

    private ResultActions resetPassword(Long userId, String temporaryPassword, String token) throws Exception {
        return putJson(userUrl(userId) + "/password", """
                {"temporaryPassword": "%s"}
                """.formatted(temporaryPassword), token);
    }

    private ResultActions changeOwnPassword(String token, String current, String next) throws Exception {
        return putJson("/api/v1/users/me/password", """
                {"currentPassword": "%s", "newPassword": "%s"}
                """.formatted(current, next), token);
    }

    private static String readAccessToken(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.accessToken");
    }

    private static String userUrl(Long id) {
        return "/api/v1/users/" + id;
    }

    // A seller stays a seller and active; only the email changes.
    private static String updateBody(String email) {
        String emailField = email == null ? "" : "\"email\": \"%s\", ".formatted(email);
        return """
                {"name": "Test User", %s"role": "SELLER", "active": true}
                """.formatted(emailField);
    }
}

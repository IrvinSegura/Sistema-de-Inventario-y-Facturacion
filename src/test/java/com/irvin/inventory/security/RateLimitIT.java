package com.irvin.inventory.security;

import static com.irvin.inventory.TestClientAddressConfiguration.from;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.irvin.inventory.AbstractIntegrationTest;
import com.irvin.inventory.auth.RecordingPasswordResetSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

// Limits from application.yaml. Each test uses its own documentation addresses (RFC 5737), never
// the random 10.x ones other tests get.
class RateLimitIT extends AbstractIntegrationTest {

    private static final String LOGIN = "/api/v1/auth/login";
    private static final String RESET_CODE = "/api/v1/auth/password-reset/code";
    private static final String RESET = "/api/v1/auth/password-reset";
    private static final String WEB_CLIENT = "http://localhost:5173";

    @Autowired
    private RateLimitProperties properties;

    @Autowired
    private RecordingPasswordResetSender sender;

    @Test
    void codeRequestsArePerAddressWhateverTheAccountAndNothingIsSentOnceLimited() throws Exception {
        String client = "203.0.113.10";
        for (int i = 0; i < properties.passwordResetCode().capacity(); i++) {
            requestCode(client, "nobody-" + UUID.randomUUID() + "@test.com").andExpect(status().isAccepted());
        }

        CreatedUser seller = createSeller();
        String retryAfter = requestCode(client, seller.email())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value("Too many requests, please retry later"))
                .andReturn().getResponse().getHeader(HttpHeaders.RETRY_AFTER);
        // About one refill period: the bucket is empty, minus what refilled while the test ran.
        long refillSeconds = properties.passwordResetCode().refillPeriod().toSeconds();
        assertThat(Long.parseLong(retryAfter)).isBetween(refillSeconds - 30, refillSeconds);
        assertThat(sender.nothingSentTo(seller.email())).isTrue();

        // Another address keeps its own budget and still gets the anti-enumeration 202.
        requestCode("203.0.113.11", seller.email()).andExpect(status().isAccepted());
        assertThat(sender.next(seller.email()).code()).matches("\\d{6}");
    }

    @Test
    void resetAttemptsArePerAddressAcrossAccounts() throws Exception {
        String client = "203.0.113.20";
        for (int i = 0; i < properties.passwordReset().capacity(); i++) {
            reset(client, "nobody-" + UUID.randomUUID() + "@test.com").andExpect(status().isBadRequest());
        }
        reset(client, "nobody-" + UUID.randomUUID() + "@test.com")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        reset("203.0.113.21", "nobody-" + UUID.randomUUID() + "@test.com").andExpect(status().isBadRequest());
    }

    // Invalid bodies count too: the limit applies before the body is read.
    @Test
    void loginIsLimitedPerAddressEvenWithTheRightPassword() throws Exception {
        String client = "203.0.113.30";
        for (int i = 0; i < properties.login().capacity(); i++) {
            loginFrom(client, "{}").andExpect(status().isBadRequest());
        }
        String adminCredentials = """
                {"email": "%s", "password": "%s"}
                """.formatted(ADMIN_EMAIL, ADMIN_PASSWORD);
        loginFrom(client, adminCredentials).andExpect(status().isTooManyRequests());
        loginFrom("203.0.113.31", adminCredentials).andExpect(status().isOk());
    }

    // The web client must be able to read Retry-After across origins.
    @Test
    void tooManyRequestsCarriesCorsHeaders() throws Exception {
        String client = "203.0.113.40";
        for (int i = 0; i < properties.login().capacity(); i++) {
            loginFrom(client, "{}");
        }
        mockMvc.perform(post(LOGIN).with(from(client))
                        .header(HttpHeaders.ORIGIN, WEB_CLIENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, WEB_CLIENT))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        containsString(HttpHeaders.RETRY_AFTER)));
    }

    private ResultActions requestCode(String client, String email) throws Exception {
        return postFrom(client, RESET_CODE, """
                {"email": "%s", "channel": "EMAIL", "language": "en"}
                """.formatted(email));
    }

    private ResultActions reset(String client, String email) throws Exception {
        return postFrom(client, RESET, """
                {"email": "%s", "code": "123456", "newPassword": "new-password-123"}
                """.formatted(email));
    }

    private ResultActions loginFrom(String client, String json) throws Exception {
        return postFrom(client, LOGIN, json);
    }

    private ResultActions postFrom(String client, String url, String json) throws Exception {
        return mockMvc.perform(post(url).with(from(client)).contentType(MediaType.APPLICATION_JSON).content(json));
    }
}

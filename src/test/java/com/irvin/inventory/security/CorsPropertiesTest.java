package com.irvin.inventory.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class CorsPropertiesTest {

    @Test
    void blankOriginsAreDropped() {
        assertThat(new CorsProperties(List.of("")).allowedOrigins()).isEmpty();
        assertThat(new CorsProperties(null).allowedOrigins()).isEmpty();
        assertThat(new CorsProperties(List.of(" https://app.example.com ", "http://localhost:5173")).allowedOrigins())
                .containsExactly("https://app.example.com", "http://localhost:5173");
    }

    @Test
    void valuesThatAreNotOriginsAreRejected() {
        for (String origin : List.of("https://app.example.com/", "https://app.example.com/path", "*",
                "https://*.example.com", "app.example.com")) {
            assertThatThrownBy(() -> new CorsProperties(List.of(origin)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}

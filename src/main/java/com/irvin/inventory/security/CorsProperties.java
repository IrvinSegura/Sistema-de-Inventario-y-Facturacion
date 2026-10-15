package com.irvin.inventory.security;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Browser origins (scheme + host [+ port]) of the web clients allowed to call the API.
@ConfigurationProperties("app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        // An empty env var binds as [""]; blanks mean "no web client".
        allowedOrigins = allowedOrigins == null
                ? List.of()
                : allowedOrigins.stream().map(String::trim).filter(origin -> !origin.isEmpty()).toList();
        for (String origin : allowedOrigins) {
            // Browsers send the origin without a path or trailing slash; anything else would never match.
            if (!origin.matches("https?://[^/*]+")) {
                throw new IllegalArgumentException(
                        "app.cors.allowed-origins must be origins like https://app.example.com, got: " + origin);
            }
        }
    }
}

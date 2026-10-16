package com.irvin.inventory.common.storage;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Cloudinary is used when cloudName is set; otherwise, if enabled, a local folder served at /images/**.
@ConfigurationProperties("app.images")
public record ImageStorageProperties(Local local, Cloudinary cloudinary) {

    // baseUrl is the public URL of the folder, e.g. http://localhost:8080/images.
    public record Local(boolean enabled, Path dir, String baseUrl) {
    }

    public record Cloudinary(String cloudName, String apiKey, String apiSecret) {
    }
}

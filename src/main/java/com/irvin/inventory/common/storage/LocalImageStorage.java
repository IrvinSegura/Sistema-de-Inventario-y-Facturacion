package com.irvin.inventory.common.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Local development: images in a folder, served by this API at /images/** (public, like a CDN).
// Not for Render: its disk is wiped on every deploy, so production uses Cloudinary.
@Slf4j
@Component
@ConditionalOnExpression("'${app.images.cloudinary.cloud-name:}'.isBlank() and ${app.images.local.enabled:false}")
public class LocalImageStorage implements ImageStorage, WebMvcConfigurer {

    static final String URL_PATH = "/images/";

    private final Path dir;
    private final String baseUrl;

    public LocalImageStorage(ImageStorageProperties properties) {
        this.dir = properties.local().dir().toAbsolutePath().normalize();
        this.baseUrl = properties.local().baseUrl().replaceAll("/+$", "");
    }

    @Override
    public String store(String key, byte[] content, ImageType type) {
        String fileName = key + "." + type.extension();
        Path file = resolve(fileName);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, content, StandardOpenOption.CREATE_NEW);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not save image " + fileName, ex);
        }
        return baseUrl + "/" + fileName;
    }

    @Override
    public void delete(String url) {
        if (url == null || !url.startsWith(baseUrl + "/")) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(url.substring(baseUrl.length() + 1)));
        } catch (IOException | IllegalArgumentException ex) {
            log.warn("Could not delete image {}: {}", url, ex.getMessage());
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(URL_PATH + "**").addResourceLocations(dir.toUri().toString());
    }

    // Keeps every path inside the folder, whatever the name contains.
    private Path resolve(String fileName) {
        Path file = dir.resolve(fileName).normalize();
        if (!file.startsWith(dir)) {
            throw new IllegalArgumentException("Image path outside the images folder: " + fileName);
        }
        return file;
    }
}

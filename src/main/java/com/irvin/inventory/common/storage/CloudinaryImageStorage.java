package com.irvin.inventory.common.storage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// Images on Cloudinary through its signed Upload API (no SDK needed). Active when a cloud name is set.
@Slf4j
@Component
@ConditionalOnExpression("!'${app.images.cloudinary.cloud-name:}'.isBlank()")
public class CloudinaryImageStorage implements ImageStorage {

    private static final String API_URL = "https://api.cloudinary.com/v1_1/";

    private final ImageStorageProperties.Cloudinary properties;
    private final Clock clock;
    private final RestClient restClient;
    // Delivery URLs look like https://res.cloudinary.com/<cloud>/image/upload/v123/<public id>.<ext>.
    private final Pattern deliveryUrl;

    public CloudinaryImageStorage(ImageStorageProperties properties, Clock clock) {
        this.properties = properties.cloudinary();
        this.clock = clock;
        this.restClient = RestClient.builder().baseUrl(API_URL + this.properties.cloudName()).build();
        this.deliveryUrl = Pattern.compile("^https://res\\.cloudinary\\.com/" + Pattern.quote(this.properties.cloudName())
                + "/image/upload/(?:v\\d+/)?(.+)\\.[A-Za-z0-9]+$");
    }

    @Override
    public String store(String key, byte[] content, ImageType type) {
        MultiValueMap<String, Object> form = signedForm(Map.of("public_id", key));
        form.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return key.substring(key.lastIndexOf('/') + 1) + "." + type.extension();
            }
        });
        UploadResponse response = restClient.post()
                .uri("/image/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .body(UploadResponse.class);
        if (response == null || response.secureUrl() == null) {
            throw new IllegalStateException("Cloudinary returned no URL for " + key);
        }
        return response.secureUrl();
    }

    @Override
    public void delete(String url) {
        Matcher matcher = url == null ? null : deliveryUrl.matcher(url);
        if (matcher == null || !matcher.matches()) {
            return;
        }
        try {
            restClient.post()
                    .uri("/image/destroy")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(signedForm(Map.of("public_id", matcher.group(1))))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            log.warn("Could not delete image {}: {}", url, ex.getMessage());
        }
    }

    private MultiValueMap<String, Object> signedForm(Map<String, String> params) {
        SortedMap<String, String> signed = new TreeMap<>(params);
        signed.put("timestamp", String.valueOf(clock.instant().getEpochSecond()));
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        signed.forEach(form::add);
        form.add("api_key", properties.apiKey());
        form.add("signature", sign(signed, properties.apiSecret()));
        return form;
    }

    // Cloudinary's rule: SHA-1 of the parameters sorted by name ("a=1&b=2") followed by the API secret.
    static String sign(SortedMap<String, String> params, String apiSecret) {
        String payload = params.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&")) + apiSecret;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1 is not available", ex);
        }
    }

    private record UploadResponse(@JsonProperty("secure_url") String secureUrl) {
    }
}

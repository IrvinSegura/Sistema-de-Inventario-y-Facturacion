package com.irvin.inventory.common.storage;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

// Image formats browsers display; detected from the file's first bytes, never from its name or the
// client's Content-Type, so a renamed HTML or SVG file can't be served as an image.
public enum ImageType {

    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg"),
    WEBP("image/webp", "webp"),
    GIF("image/gif", "gif");

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private final String mediaType;
    private final String extension;

    ImageType(String mediaType, String extension) {
        this.mediaType = mediaType;
        this.extension = extension;
    }

    public String mediaType() {
        return mediaType;
    }

    public String extension() {
        return extension;
    }

    public static Optional<ImageType> detect(byte[] content) {
        if (startsWith(content, PNG_SIGNATURE)) {
            return Optional.of(PNG);
        }
        if (startsWith(content, JPEG_SIGNATURE)) {
            return Optional.of(JPEG);
        }
        // RIFF container: "RIFF", 4 size bytes, then "WEBP".
        if (startsWith(content, ascii("RIFF")) && content.length >= 12
                && Arrays.equals(content, 8, 12, ascii("WEBP"), 0, 4)) {
            return Optional.of(WEBP);
        }
        if (startsWith(content, ascii("GIF87a")) || startsWith(content, ascii("GIF89a"))) {
            return Optional.of(GIF);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        return content.length >= prefix.length && Arrays.equals(content, 0, prefix.length, prefix, 0, prefix.length);
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }
}

package com.irvin.inventory.catalog.dto;

// Validation rules shared by product requests.
final class ProductRules {

    // 8 to 14 digits (covers EAN-8, UPC-A, EAN-13 and GTIN-14), same as the DB CHECK.
    static final String BARCODE_PATTERN = "^[0-9]{8,14}$";
    static final String BARCODE_MESSAGE = "must be 8 to 14 digits";

    // Only URL-safe characters: quotes or angle brackets could break out of an HTML attribute.
    static final String IMAGE_URL_PATTERN = "^https?://[A-Za-z0-9._~:/?#\\[\\]@!$&()*+,;=%-]+$";
    static final String IMAGE_URL_MESSAGE = "must be an http(s) URL";

    private ProductRules() {
    }
}

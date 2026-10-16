package com.irvin.inventory.common.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.TreeMap;

import org.junit.jupiter.api.Test;

class CloudinaryImageStorageTest {

    // Example from Cloudinary's "Generating authentication signatures" documentation.
    @Test
    void signsSortedParametersFollowedByTheSecret() {
        TreeMap<String, String> params = new TreeMap<>();
        params.put("timestamp", "1315060510");
        params.put("public_id", "sample_image");
        params.put("eager", "w_400,h_300,c_pad|w_260,h_200,c_crop");

        assertThat(CloudinaryImageStorage.sign(params, "abcd")).isEqualTo("bfd09f95f331f558cbd1320e67aa8d488770583e");
    }
}

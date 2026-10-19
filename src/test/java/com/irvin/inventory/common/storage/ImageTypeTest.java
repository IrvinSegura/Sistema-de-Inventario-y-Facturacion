package com.irvin.inventory.common.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class ImageTypeTest {

    @Test
    void detectsFormatsFromTheirSignature() {
        assertThat(ImageType.detect(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0})).contains(ImageType.PNG);
        assertThat(ImageType.detect(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0})).contains(ImageType.JPEG);
        assertThat(ImageType.detect(ascii("RIFF\0\0\0\0WEBPVP8 "))).contains(ImageType.WEBP);
        assertThat(ImageType.detect(ascii("GIF89a..."))).contains(ImageType.GIF);
    }

    @Test
    void rejectsAnythingElseWhateverItsName() {
        assertThat(ImageType.detect(ascii("<svg xmlns=\"http://www.w3.org/2000/svg\"/>"))).isEmpty();
        assertThat(ImageType.detect(ascii("<html><script>alert(1)</script>"))).isEmpty();
        // A RIFF file that isn't WebP (e.g. a WAV sound).
        assertThat(ImageType.detect(ascii("RIFF\0\0\0\0WAVEfmt "))).isEmpty();
        assertThat(ImageType.detect(new byte[] {(byte) 0x89, 'P'})).isEmpty();
        assertThat(ImageType.detect(new byte[0])).isEmpty();
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }
}

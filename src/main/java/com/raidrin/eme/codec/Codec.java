package com.raidrin.eme.codec;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Codec {
    public static String encode(String originalString) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(originalString.getBytes(StandardCharsets.UTF_8));
    }

    public static String decode(String encodedString) {
        byte[] decodedBytes = Base64.getUrlDecoder().decode(encodedString);
        return new String(decodedBytes, StandardCharsets.UTF_8);
    }
}

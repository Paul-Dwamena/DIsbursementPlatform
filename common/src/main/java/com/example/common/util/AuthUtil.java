package com.example.common.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class AuthUtil {

    private AuthUtil() {
        // prevent instantiation
    }

    /**
     * Generate a Basic Authentication header value.
     *
     * @param userId the API user ID
     * @param apiKey the API key
     * @return the header value (e.g. "Basic dXNlcjpwYXNzd29yZA==")
     */
    public static String basicAuthHeader(String userId, String apiKey) {
        String credentials = userId + ":" + apiKey;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}

package com.kantara.config;

public final class LocalTestConfigLoader {

    private static final String TEST_API_KEY = "API_KEY_HERE";
    private static final String TEST_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models";
    private static final String TEST_MODEL = "gemini-3-flash-preview";

    private LocalTestConfigLoader() {
    }

    public static Config load() {
        if (TEST_API_KEY.isBlank() || "API_KEY_HERE".equals(TEST_API_KEY)) {
            throw new IllegalStateException("[Kantara] ERROR: Set TEST_API_KEY in LocalTestConfigLoader before local testing.");
        }
        return new Config(TEST_API_KEY, TEST_ENDPOINT, TEST_MODEL);
    }
}

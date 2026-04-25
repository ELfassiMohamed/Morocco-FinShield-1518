package com.kantara.config;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigLoader {

    private static final String DEFAULT_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models";
    private static final String DEFAULT_MODEL = "gemini-1.5-flash";

    private ConfigLoader() {
    }

    public static Config load(String configPath) {
        String apiKey = env("KANTARA_API_KEY");
        String endpoint = env("KANTARA_ENDPOINT");
        String model = env("KANTARA_MODEL");

        if (!isBlank(configPath)) {
            JsonNode configRoot = readConfigFile(configPath.trim());
            apiKey = firstNonBlank(apiKey, readText(configRoot, "apiKey", "api_key"));
            endpoint = firstNonBlank(endpoint, readText(configRoot, "endpoint"));
            model = firstNonBlank(model, readText(configRoot, "model"));
        }

        endpoint = firstNonBlank(endpoint, DEFAULT_ENDPOINT);
        model = firstNonBlank(model, DEFAULT_MODEL);

        if (isBlank(apiKey)) {
            throw new IllegalStateException("[Kantara] ERROR: Missing API key (KANTARA_API_KEY)");
        }

        return new Config(apiKey, endpoint, model);
    }

    private static JsonNode readConfigFile(String configPath) {
        Path path = Path.of(configPath);
        if (!Files.exists(path)) {
            throw new IllegalStateException("[Kantara] ERROR: Config file not found: " + configPath);
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readTree(path.toFile());
        } catch (JacksonException e) {
            throw new IllegalStateException("[Kantara] ERROR: Invalid config JSON: " + configPath);
        } catch (IOException e) {
            throw new IllegalStateException("[Kantara] ERROR: Unable to read config file: " + configPath);
        }
    }

    private static String readText(JsonNode root, String... fields) {
        if (root == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode node = root.path(field);
            if (node.isMissingNode() || node.isNull()) {
                continue;
            }
            String value = node.asText("").trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return isBlank(value) ? null : value.trim();
    }

    private static String firstNonBlank(String first, String second) {
        return !isBlank(first) ? first : second;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

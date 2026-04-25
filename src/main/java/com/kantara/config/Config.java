package com.kantara.config;

public class Config {

    private final String apiKey;
    private final String endpoint;
    private final String model;

    public Config(String apiKey, String endpoint, String model) {
        this.apiKey = apiKey;
        this.endpoint = endpoint;
        this.model = model;
    }

    public String apiKey() {
        return apiKey;
    }

    public String endpoint() {
        return endpoint;
    }

    public String model() {
        return model;
    }
}

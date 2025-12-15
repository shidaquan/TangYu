package com.example.tangyu.config;

import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.Objects;

/**
 * Configuration for DashScope Fun-ASR access (API Key + optional overrides).
 */
public class DashScopeConfig {
    private static final String DEFAULT_BASE_URL = "wss://dashscope.aliyuncs.com/api-ws/v1/inference";
    private static final String DEFAULT_MODEL = "fun-asr-realtime";
    private static final String DEFAULT_LANGUAGE_HINTS = "zh,en";

    private final String apiKey;
    private final String baseWebsocketUrl;
    private final String model;
    private final String[] languageHints;

    public DashScopeConfig(String apiKey, String baseWebsocketUrl, String model, String[] languageHints) {
        this.apiKey = Objects.requireNonNull(apiKey, "DashScope API Key is required");
        this.baseWebsocketUrl = Objects.requireNonNullElse(baseWebsocketUrl, DEFAULT_BASE_URL);
        this.model = Objects.requireNonNullElse(model, DEFAULT_MODEL);
        this.languageHints = languageHints != null ? languageHints : parseHints(DEFAULT_LANGUAGE_HINTS);
    }

    public static DashScopeConfig fromEnvironment() {
        String apiKey = require("DASHSCOPE_API_KEY", "dashscope.apiKey");
        String baseUrl = optional("DASHSCOPE_BASE_URL", "dashscope.baseUrl", DEFAULT_BASE_URL);
        String model = optional("DASHSCOPE_ASR_MODEL", "dashscope.asr.model", DEFAULT_MODEL);
        String hints = optional("DASHSCOPE_ASR_LANGUAGE_HINTS", "dashscope.asr.languageHints", DEFAULT_LANGUAGE_HINTS);
        return new DashScopeConfig(apiKey, baseUrl, model, parseHints(hints));
    }

    /**
     * Build config from Spring Environment (application.yml/application.properties), with env/system fallback.
     */
    public static DashScopeConfig fromEnvironment(Environment env) {
        String apiKey = require(env, "DASHSCOPE_API_KEY", "dashscope.apiKey");
        String baseUrl = optional(env, "DASHSCOPE_BASE_URL", "dashscope.baseUrl", DEFAULT_BASE_URL);
        String model = optional(env, "DASHSCOPE_ASR_MODEL", "dashscope.asr.model", DEFAULT_MODEL);
        String hints = optional(env, "DASHSCOPE_ASR_LANGUAGE_HINTS", "dashscope.asr.languageHints", DEFAULT_LANGUAGE_HINTS);
        return new DashScopeConfig(apiKey, baseUrl, model, parseHints(hints));
    }

    private static String require(String envName, String propertyName) {
        String value = optional(envName, propertyName, null);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required configuration for " + envName + " or system property " + propertyName);
        }
        return value;
    }

    private static String require(Environment env, String envName, String propertyName) {
        String value = optional(env, envName, propertyName, null);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required configuration for " + envName + " or property " + propertyName);
        }
        return value;
    }

    private static String optional(String envName, String propertyName, String defaultValue) {
        String value = System.getenv(envName);
        if (value == null || value.isBlank()) {
            value = System.getProperty(propertyName);
        }
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    private static String optional(Environment env, String envName, String propertyName, String defaultValue) {
        String value = env != null ? env.getProperty(propertyName) : null;
        if (value == null || value.isBlank()) {
            value = System.getenv(envName);
        }
        if (value == null || value.isBlank()) {
            value = System.getProperty(propertyName);
        }
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    private static String[] parseHints(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getBaseWebsocketUrl() {
        return baseWebsocketUrl;
    }

    public String getModel() {
        return model;
    }

    public String[] getLanguageHints() {
        return languageHints;
    }
}

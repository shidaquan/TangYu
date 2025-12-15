package com.example.tangyu.config;

import java.util.Objects;

/**
 * Configuration for DashScope Qwen TTS Realtime access.
 */
public class TtsConfig {
    private static final String DEFAULT_BASE_URL = "wss://dashscope.aliyuncs.com/api-ws/v1/inference";
    private static final String DEFAULT_MODEL = "qwen3-tts-flash-realtime-2025-11-27";
    private static final String DEFAULT_VOICE = "zh-CN-XiaoyiNeural";
    private static final int DEFAULT_SAMPLE_RATE = 24000;
    private static final String DEFAULT_FORMAT = "pcm";

    private final String apiKey;
    private final String baseWebsocketUrl;
    private final String model;
    private final String voice;
    private final int sampleRate;
    private final String format;

    public TtsConfig(String apiKey, String baseWebsocketUrl, String model, 
                     String voice, int sampleRate, String format) {
        this.apiKey = Objects.requireNonNull(apiKey, "DashScope API Key is required");
        this.baseWebsocketUrl = Objects.requireNonNullElse(baseWebsocketUrl, DEFAULT_BASE_URL);
        this.model = Objects.requireNonNullElse(model, DEFAULT_MODEL);
        this.voice = Objects.requireNonNullElse(voice, DEFAULT_VOICE);
        this.sampleRate = sampleRate > 0 ? sampleRate : DEFAULT_SAMPLE_RATE;
        this.format = Objects.requireNonNullElse(format, DEFAULT_FORMAT);
    }

    public static TtsConfig fromEnvironment() {
        String apiKey = require("DASHSCOPE_API_KEY", "dashscope.apiKey");
        String baseUrl = optional("DASHSCOPE_TTS_BASE_URL", "dashscope.tts.baseUrl", DEFAULT_BASE_URL);
        String model = optional("DASHSCOPE_TTS_MODEL", "dashscope.tts.model", DEFAULT_MODEL);
        String voice = optional("DASHSCOPE_TTS_VOICE", "dashscope.tts.voice", DEFAULT_VOICE);
        int sampleRate = Integer.parseInt(optional("DASHSCOPE_TTS_SAMPLE_RATE", "dashscope.tts.sampleRate", String.valueOf(DEFAULT_SAMPLE_RATE)));
        String format = optional("DASHSCOPE_TTS_FORMAT", "dashscope.tts.format", DEFAULT_FORMAT);
        return new TtsConfig(apiKey, baseUrl, model, voice, sampleRate, format);
    }

    private static String require(String envName, String propertyName) {
        String value = optional(envName, propertyName, null);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required configuration for " + envName + " or system property " + propertyName);
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

    public String getApiKey() {
        return apiKey;
    }

    public String getBaseWebsocketUrl() {
        return baseWebsocketUrl;
    }

    public String getModel() {
        return model;
    }

    public String getVoice() {
        return voice;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public String getFormat() {
        return format;
    }
}


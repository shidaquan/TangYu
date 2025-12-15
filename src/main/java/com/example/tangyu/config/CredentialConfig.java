package com.example.tangyu.config;

import org.springframework.core.env.Environment;

import java.util.Objects;

/**
 * Simple holder for Aliyun credential settings sourced from environment variables
 * or system properties.
 */
public class CredentialConfig {
    private final String accessKeyId;
    private final String accessKeySecret;
    private final String appKey;

    public CredentialConfig(String accessKeyId, String accessKeySecret, String appKey) {
        this.accessKeyId = Objects.requireNonNull(accessKeyId, "AccessKeyId is required");
        this.accessKeySecret = Objects.requireNonNull(accessKeySecret, "AccessKeySecret is required");
        this.appKey = Objects.requireNonNull(appKey, "AppKey is required");
    }

    public static CredentialConfig fromEnvironment() {
        String accessKeyId = getValue("ALIBABA_CLOUD_ACCESS_KEY_ID", "alibaba.cloud.accessKeyId");
        String accessKeySecret = getValue("ALIBABA_CLOUD_ACCESS_KEY_SECRET", "alibaba.cloud.accessKeySecret");
        String appKey = getValue("ALIBABA_CLOUD_APP_KEY", "alibaba.cloud.appKey");
        return new CredentialConfig(accessKeyId, accessKeySecret, appKey);
    }

    /**
     * Build config from Spring Environment (application.yml/application.properties), with env/system fallback.
     */
    public static CredentialConfig fromEnvironment(Environment env) {
        String accessKeyId = getValue(env, "ALIBABA_CLOUD_ACCESS_KEY_ID", "alibaba.cloud.accessKeyId");
        String accessKeySecret = getValue(env, "ALIBABA_CLOUD_ACCESS_KEY_SECRET", "alibaba.cloud.accessKeySecret");
        String appKey = getValue(env, "ALIBABA_CLOUD_APP_KEY", "alibaba.cloud.appKey");
        return new CredentialConfig(accessKeyId, accessKeySecret, appKey);
    }

    private static String getValue(String envName, String propertyName) {
        String value = System.getenv(envName);
        if (value == null || value.isBlank()) {
            value = System.getProperty(propertyName);
        }
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required configuration for " + envName + " or system property " + propertyName);
        }
        return value;
    }

    private static String getValue(Environment env, String envName, String propertyName) {
        String value = env != null ? env.getProperty(propertyName) : null;
        if (value == null || value.isBlank()) {
            value = System.getenv(envName);
        }
        if (value == null || value.isBlank()) {
            value = System.getProperty(propertyName);
        }
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required configuration for " + envName + " or property " + propertyName);
        }
        return value;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public String getAccessKeySecret() {
        return accessKeySecret;
    }

    public String getAppKey() {
        return appKey;
    }
}

package com.example.tangyu.speech;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;

class TokenResponse {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String id;
    private final Instant expireTime;

    TokenResponse(String id, Instant expireTime) {
        this.id = id;
        this.expireTime = expireTime;
    }

    static TokenResponse parse(String rawJson) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(rawJson);
            JsonNode tokenNode = root.path("Token");
            String id = tokenNode.path("Id").asText();
            Instant expireTime = Instant.parse(tokenNode.path("ExpireTime").asText());
            return new TokenResponse(id, expireTime);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to parse token response", e);
        }
    }

    public String getId() {
        return id;
    }

    public Instant getExpireTime() {
        return expireTime;
    }
}

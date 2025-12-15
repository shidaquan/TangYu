package com.example.tangyu.server;

import com.example.tangyu.config.CredentialConfig;
import com.example.tangyu.config.DashScopeConfig;
import com.example.tangyu.speech.AsrClient;
import com.example.tangyu.speech.TtsClient;
import com.example.tangyu.speech.TokenClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lightweight HTTP server that exposes ASR and TTS as simple REST endpoints.
 *
 * Endpoints:
 *  - GET  /health                 -> 200 OK
 *  - POST /api/asr?format=pcm&sampleRate=16000 (body: audio bytes)
 *        -> { "result": <Fun-ASR JSON> }
 *  - POST /api/tts (body: JSON { text, voice, format, sampleRate })
 *        -> { "audioBase64": "...", "format": "...", "sampleRate": 16000 }
 *
 * Credentials are read from the same environment variables/system properties used by the CLI.
 */
public class SpeechApiServer {
    private static final Logger LOG = LoggerFactory.getLogger(SpeechApiServer.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final int port;
    private final AsrClient asrClient;
    private final TtsClient ttsClient;
    private HttpServer server;
    private ExecutorService executor;

    public SpeechApiServer(int port) {
        this.port = port;
        DashScopeConfig dashScopeConfig = DashScopeConfig.fromEnvironment();
        CredentialConfig credentialConfig = CredentialConfig.fromEnvironment();
        TokenClient tokenClient = new TokenClient(credentialConfig);
        this.asrClient = new AsrClient(dashScopeConfig);
        this.ttsClient = new TtsClient(credentialConfig, tokenClient);
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            executor = Executors.newCachedThreadPool();
            server.setExecutor(executor);

            server.createContext("/health", new HealthHandler());
            server.createContext("/api/asr", new AsrHandler());
            server.createContext("/api/tts", new TtsHandler());

            server.start();
            LOG.info("Speech API server started on port {}", port);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start HTTP server", e);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdown();
        }
    }

    private static void writeJson(HttpExchange exchange, int statusCode, Object body) throws IOException {
        byte[] bytes = OBJECT_MAPPER.writeValueAsBytes(body);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static Map<String, String> queryParams(URI uri) {
        String query = uri.getQuery();
        if (query == null || query.isBlank()) {
            return Map.of();
        }
        java.util.Map<String, String> result = new java.util.HashMap<>();
        for (String part : query.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2) {
                String key = java.net.URLDecoder.decode(kv[0], java.nio.charset.StandardCharsets.UTF_8);
                String value = java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8);
                result.put(key, value);
            }
        }
        return result;
    }

    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            writeJson(exchange, 200, Map.of("status", "ok"));
        }
    }

    private class AsrHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            Map<String, String> params = queryParams(exchange.getRequestURI());
            String format = params.getOrDefault("format", "pcm");
            int sampleRate = 16000;
            if (params.containsKey("sampleRate")) {
                try {
                    sampleRate = Integer.parseInt(params.get("sampleRate"));
                } catch (NumberFormatException e) {
                    writeJson(exchange, 400, Map.of("error", "Invalid sampleRate parameter"));
                    return;
                }
            }

            byte[] audioBytes;
            try (InputStream is = exchange.getRequestBody()) {
                audioBytes = is.readAllBytes();
            }

            if (audioBytes.length == 0) {
                writeJson(exchange, 400, Map.of("error", "Empty request body, please POST raw audio bytes"));
                return;
            }

            Path tempFile = Files.createTempFile("asr-", "." + format);
            try {
                Files.write(tempFile, audioBytes);
                String asrResult = asrClient.transcribe(tempFile, format, sampleRate);

                ObjectNode response = OBJECT_MAPPER.createObjectNode();
                try {
                    JsonNode parsed = OBJECT_MAPPER.readTree(asrResult);
                    response.set("result", parsed);
                } catch (Exception ignore) {
                    response.put("result", asrResult);
                }
                writeJson(exchange, 200, response);
            } catch (IllegalArgumentException e) {
                LOG.warn("Bad ASR request: {}", e.getMessage());
                writeJson(exchange, 400, Map.of("error", e.getMessage()));
            } catch (Exception e) {
                LOG.error("ASR processing failed", e);
                writeJson(exchange, 500, Map.of("error", "ASR failed: " + e.getMessage()));
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }
    }

    private class TtsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            TtsRequest request;
            try (InputStream is = exchange.getRequestBody()) {
                request = OBJECT_MAPPER.readValue(is, TtsRequest.class);
            } catch (Exception e) {
                writeJson(exchange, 400, Map.of("error", "Invalid JSON payload: " + e.getMessage()));
                return;
            }

            if (request.text == null || request.text.isBlank()) {
                writeJson(exchange, 400, Map.of("error", "Field 'text' is required"));
                return;
            }

            String voice = request.voice != null ? request.voice : "xiaoyun";
            String format = request.format != null ? request.format : "wav";
            int sampleRate = request.sampleRate != null ? request.sampleRate : 16000;

            Path tempFile = Files.createTempFile("tts-", "." + format);
            try {
                ttsClient.synthesizeToFile(request.text, voice, format, sampleRate, tempFile);
                byte[] audioBytes = Files.readAllBytes(tempFile);
                String base64 = Base64.getEncoder().encodeToString(audioBytes);

                ObjectNode response = OBJECT_MAPPER.createObjectNode();
                response.put("audioBase64", base64);
                response.put("format", format);
                response.put("sampleRate", sampleRate);
                writeJson(exchange, 200, response);
            } catch (IllegalArgumentException e) {
                LOG.warn("Bad TTS request: {}", e.getMessage());
                writeJson(exchange, 400, Map.of("error", e.getMessage()));
            } catch (Exception e) {
                LOG.error("TTS processing failed", e);
                writeJson(exchange, 500, Map.of("error", "TTS failed: " + e.getMessage()));
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }
    }

    private static class TtsRequest {
        public String text;
        public String voice;
        public String format;
        public Integer sampleRate;
    }
}

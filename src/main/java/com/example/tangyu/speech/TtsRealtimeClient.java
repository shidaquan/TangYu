package com.example.tangyu.speech;

import com.example.tangyu.config.TtsConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Real-time TTS client using DashScope Qwen TTS Realtime WebSocket API.
 * Supports streaming text-to-speech synthesis with low latency.
 * 
 * Reference: https://help.aliyun.com/zh/model-studio/qwen-tts-realtime-java-sdk
 */
public class TtsRealtimeClient {
    private static final Logger LOG = LoggerFactory.getLogger(TtsRealtimeClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TtsConfig ttsConfig;
    private final OkHttpClient httpClient;

    public TtsRealtimeClient(TtsConfig ttsConfig) {
        this.ttsConfig = Objects.requireNonNull(ttsConfig);
        this.httpClient = new OkHttpClient.Builder()
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Synthesize text to audio file (synchronous).
     * 
     * @param text       text to synthesize
     * @param outputFile output audio file path
     * @return the output file path
     */
    public Path synthesizeToFile(String text, Path outputFile) {
        return synthesizeToFile(text, null, outputFile);
    }

    /**
     * Synthesize text to audio file with custom voice.
     * 
     * @param text       text to synthesize
     * @param voice      voice name (optional, uses config default if null)
     * @param outputFile output audio file path
     * @return the output file path
     */
    public Path synthesizeToFile(String text, String voice, Path outputFile) {
        validate(text, outputFile);
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);
        StringBuilder errorMessage = new StringBuilder();
        
        try {
            // Create WebSocket connection
            String wsUrl = ttsConfig.getBaseWebsocketUrl().replace("https://", "wss://").replace("http://", "ws://");
            if (!wsUrl.contains("/api-ws/v1/inference")) {
                wsUrl = wsUrl + "/api-ws/v1/inference";
            }
            
            Request request = new Request.Builder()
                    .url(wsUrl)
                    .build();
            
            WebSocket webSocket = httpClient.newWebSocket(request, new TtsWebSocketListener(
                    text, voice != null ? voice : ttsConfig.getVoice(), 
                    outputFile, latch, success, errorMessage));
            
            // Wait for completion
            boolean completed = latch.await(60, TimeUnit.SECONDS);
            
            if (!completed) {
                webSocket.close(1000, "Timeout");
                throw new RuntimeException("TTS synthesis timeout");
            }
            
            if (!success.get()) {
                throw new RuntimeException("TTS synthesis failed: " + errorMessage.toString());
            }
            
            LOG.info("TTS synthesis completed. Audio saved to {}", outputFile.toAbsolutePath());
            return outputFile;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("TTS synthesis interrupted", e);
        } catch (Exception e) {
            LOG.error("Failed to synthesize text to file", e);
            throw new RuntimeException("Failed to synthesize text: " + e.getMessage(), e);
        }
    }

    /**
     * Start streaming TTS synthesis with callback.
     * 
     * @param onAudioData callback for audio data chunks
     * @param voice       voice name (optional)
     * @return WebSocket instance for sending text
     */
    public WebSocket startStreaming(Consumer<byte[]> onAudioData, String voice) {
        return startStreaming(onAudioData, voice, null, null);
    }

    /**
     * Start streaming TTS synthesis with full callbacks.
     * 
     * @param onAudioData callback for audio data chunks
     * @param voice       voice name (optional)
     * @param onError     error callback (optional)
     * @param onComplete  completion callback (optional)
     * @return WebSocket instance for sending text
     */
    public WebSocket startStreaming(Consumer<byte[]> onAudioData, String voice,
                                   Runnable onError, Runnable onComplete) {
        Objects.requireNonNull(onAudioData, "Audio data callback is required");
        
        try {
            String wsUrl = ttsConfig.getBaseWebsocketUrl().replace("https://", "wss://").replace("http://", "ws://");
            if (!wsUrl.contains("/api-ws/v1/inference")) {
                wsUrl = wsUrl + "/api-ws/v1/inference";
            }
            
            Request request = new Request.Builder()
                    .url(wsUrl)
                    .build();
            
            WebSocket webSocket = httpClient.newWebSocket(request, new TtsStreamWebSocketListener(
                    voice != null ? voice : ttsConfig.getVoice(),
                    onAudioData, onError, onComplete));
            
            // Send initial session configuration
            sendSessionConfig(webSocket, voice != null ? voice : ttsConfig.getVoice());
            
            return webSocket;
            
        } catch (Exception e) {
            LOG.error("Failed to start streaming TTS", e);
            throw new RuntimeException("Failed to start streaming TTS: " + e.getMessage(), e);
        }
    }

    private void sendSessionConfig(WebSocket webSocket, String voice) {
        try {
            ObjectNode config = OBJECT_MAPPER.createObjectNode();
            config.put("type", "session.update");
            ObjectNode session = OBJECT_MAPPER.createObjectNode();
            ObjectNode model = OBJECT_MAPPER.createObjectNode();
            model.put("model", ttsConfig.getModel());
            model.put("voice", voice);
            model.put("sample_rate", ttsConfig.getSampleRate());
            model.put("format", ttsConfig.getFormat());
            session.set("model", model);
            config.set("session", session);
            
            String configJson = OBJECT_MAPPER.writeValueAsString(config);
            webSocket.send(configJson);
        } catch (Exception e) {
            LOG.error("Failed to send session config", e);
        }
    }

    private void sendText(WebSocket webSocket, String text) {
        try {
            ObjectNode request = OBJECT_MAPPER.createObjectNode();
            request.put("type", "input.text");
            request.put("text", text);
            
            String requestJson = OBJECT_MAPPER.writeValueAsString(request);
            webSocket.send(requestJson);
        } catch (Exception e) {
            LOG.error("Failed to send text", e);
        }
    }

    private void validate(String text, Path outputFile) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text to synthesize must be provided");
        }
        if (outputFile == null) {
            throw new IllegalArgumentException("Output file path is required");
        }
        try {
            if (outputFile.getParent() != null) {
                Files.createDirectories(outputFile.getParent());
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot create output directory: " + e.getMessage(), e);
        }
    }

    /**
     * WebSocket listener for file-based synthesis.
     */
    private class TtsWebSocketListener extends WebSocketListener {
        private final String text;
        private final String voice;
        private final Path outputFile;
        private final CountDownLatch latch;
        private final AtomicBoolean success;
        private final StringBuilder errorMessage;

        public TtsWebSocketListener(String text, String voice, Path outputFile,
                                   CountDownLatch latch, AtomicBoolean success, StringBuilder errorMessage) {
            this.text = text;
            this.voice = voice;
            this.outputFile = outputFile;
            this.latch = latch;
            this.success = success;
            this.errorMessage = errorMessage;
        }

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            LOG.debug("TTS WebSocket connection opened");
            sendSessionConfig(webSocket, voice);
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            try {
                ObjectNode event = (ObjectNode) OBJECT_MAPPER.readTree(text);
                String type = event.get("type").asText();
                
                if ("session.created".equals(type)) {
                    LOG.debug("TTS session created: {}", event.get("session").get("id").asText());
                    // Send text after session is created
                    sendText(webSocket, this.text);
                    // Commit
                    ObjectNode commit = OBJECT_MAPPER.createObjectNode();
                    commit.put("type", "input.text.done");
                    webSocket.send(OBJECT_MAPPER.writeValueAsString(commit));
                } else if ("response.audio.delta".equals(type)) {
                    // Decode base64 audio data
                    String delta = event.get("delta").asText();
                    if (delta != null && !delta.isEmpty()) {
                        byte[] audioData = Base64.getDecoder().decode(delta);
                        Files.write(outputFile, audioData, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    }
                } else if ("response.done".equals(type)) {
                    LOG.debug("TTS response done: {}", event.get("id").asText());
                } else if ("session.finished".equals(type)) {
                    LOG.debug("TTS session finished");
                    success.set(true);
                    webSocket.close(1000, "Done");
                    latch.countDown();
                } else if ("error".equals(type)) {
                    String error = event.has("message") ? event.get("message").asText() : "Unknown error";
                    errorMessage.append(error);
                    LOG.error("TTS error: {}", error);
                    webSocket.close(1000, "Error");
                    latch.countDown();
                }
            } catch (Exception e) {
                LOG.error("Error processing TTS WebSocket message", e);
                errorMessage.append("Error processing message: ").append(e.getMessage());
                webSocket.close(1000, "Error");
                latch.countDown();
            }
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            LOG.error("TTS WebSocket failure", t);
            errorMessage.append("WebSocket failure: ").append(t.getMessage());
            latch.countDown();
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            LOG.debug("TTS WebSocket closed: code={}, reason={}", code, reason);
        }
    }

    /**
     * WebSocket listener for streaming synthesis.
     */
    private class TtsStreamWebSocketListener extends WebSocketListener {
        private final String voice;
        private final Consumer<byte[]> onAudioData;
        private final Runnable onError;
        private final Runnable onComplete;

        public TtsStreamWebSocketListener(String voice, Consumer<byte[]> onAudioData,
                                         Runnable onError, Runnable onComplete) {
            this.voice = voice;
            this.onAudioData = onAudioData;
            this.onError = onError;
            this.onComplete = onComplete;
        }

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            LOG.debug("TTS streaming WebSocket connection opened");
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            try {
                ObjectNode event = (ObjectNode) OBJECT_MAPPER.readTree(text);
                String type = event.get("type").asText();
                
                if ("session.created".equals(type)) {
                    LOG.debug("TTS streaming session created: {}", event.get("session").get("id").asText());
                } else if ("response.audio.delta".equals(type)) {
                    // Decode base64 audio data and call callback
                    String delta = event.get("delta").asText();
                    if (delta != null && !delta.isEmpty()) {
                        byte[] audioData = Base64.getDecoder().decode(delta);
                        onAudioData.accept(audioData);
                    }
                } else if ("response.done".equals(type)) {
                    LOG.debug("TTS response done: {}", event.get("id").asText());
                } else if ("session.finished".equals(type)) {
                    LOG.debug("TTS session finished");
                    if (onComplete != null) {
                        onComplete.run();
                    }
                } else if ("error".equals(type)) {
                    String error = event.has("message") ? event.get("message").asText() : "Unknown error";
                    LOG.error("TTS error: {}", error);
                    if (onError != null) {
                        onError.run();
                    }
                }
            } catch (Exception e) {
                LOG.error("Error processing TTS streaming WebSocket message", e);
                if (onError != null) {
                    onError.run();
                }
            }
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            LOG.error("TTS streaming WebSocket failure", t);
            if (onError != null) {
                onError.run();
            }
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            LOG.debug("TTS streaming WebSocket closed: code={}, reason={}", code, reason);
            if (onComplete != null) {
                onComplete.run();
            }
        }
    }
}

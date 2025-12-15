package com.example.tangyu.robot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 通过 WebSocket 将识别文本发送给机器人服务，并等待回复的简单客户端。
 */
public class RobotClient {
    private static final Logger LOG = LoggerFactory.getLogger(RobotClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RobotConfig config;
    private final OkHttpClient httpClient;

    public RobotClient(RobotConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 发送文本并尽量获取流式返回的有效内容；等待期间累积 chunk 文本，最终返回最后的非空文本或累积文本。
     */
    public String sendAndReceive(String voiceText, String token) {
        if (voiceText == null || voiceText.isBlank()) {
            LOG.warn("Voice text is empty, skip robot call");
            return null;
        }

        Throwable lastError = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> responseHolder = new AtomicReference<>();
            AtomicReference<Throwable> errorHolder = new AtomicReference<>();
            StringBuilder chunkBuffer = new StringBuilder();

            try {
                String wsUrl = appendToken(config.getWsUrl(), token);
                Request request = new Request.Builder()
                        .url(wsUrl)
                        .build();

                WebSocket webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
                    @Override
                    public void onOpen(WebSocket webSocket, Response response) {
                        try {
                            ObjectNode payload = OBJECT_MAPPER.createObjectNode();
                            payload.put("voice", voiceText);
                            payload.put("scene", config.getScene());
                            payload.put("inputType", config.getInputType());
                            payload.put("token", token);
                            payload.put("personaId", config.getPersonaId());
                            webSocket.send(OBJECT_MAPPER.writeValueAsString(payload));
                        } catch (Exception e) {
                            errorHolder.set(e);
                            latch.countDown();
                            webSocket.close(1001, "payload_error");
                        }
                    }

                    @Override
                    public void onMessage(WebSocket webSocket, String text) {
                        try {
                            JsonNode node = OBJECT_MAPPER.readTree(text);
                            String type = node.path("type").asText("");
                            boolean done = "done".equalsIgnoreCase(type) || (node.has("done") && node.get("done").asBoolean(false));

                            String extracted = extractText(node, text);
                            if (extracted != null && !extracted.isBlank()) {
                                if ("chunk".equalsIgnoreCase(type)) {
                                    chunkBuffer.append(extracted);
                                    responseHolder.set(chunkBuffer.toString());
                                } else {
                                    responseHolder.set(extracted);
                                }
                            }

                            if (done) {
                                // 如果没有单独的 chunk 文本，则尝试用最后一次提取的内容
                                if (chunkBuffer.length() > 0) {
                                    responseHolder.set(chunkBuffer.toString());
                                }
                                latch.countDown();
                            }
                        } catch (Exception e) {
                            if (text != null && !text.isBlank()) {
                                responseHolder.set(text);
                            }
                        }
                    }

                    @Override
                    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                        errorHolder.set(t);
                        latch.countDown();
                    }

                    @Override
                    public void onClosing(WebSocket webSocket, int code, String reason) {
                        latch.countDown();
                    }
                });

                boolean completed = latch.await(120, TimeUnit.SECONDS);
                webSocketCloseSilently(webSocket);

                if (!completed) {
                    LOG.warn("Robot WS call timed out (attempt {}/{})", attempt, 3);
                    lastError = new RuntimeException("timeout");
                    continue;
                }
                if (errorHolder.get() != null) {
                    LOG.warn("Robot WS call failed (attempt {}/{}): {}", attempt, 3, errorHolder.get().getMessage());
                    lastError = errorHolder.get();
                    continue;
                }
                return responseHolder.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                lastError = e;
                LOG.warn("Robot WS call error (attempt {}/{}): {}", attempt, 3, e.getMessage());
            }
        }

        if (lastError != null) {
            LOG.warn("Robot WS call failed after retries: {}", lastError.getMessage());
        }
        return null;
    }

    /**
     * 将 token 作为查询参数拼接到 wsUrl（如果未提供则原样返回）。
     */
    private String appendToken(String wsUrl, String token) {
        if (token == null || token.isBlank()) {
            return wsUrl;
        }
        String delimiter = wsUrl.contains("?") ? "&" : "?";
        String encoded = java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8);
        return wsUrl + delimiter + "token=" + encoded;
    }

    /**
     * 尝试从大模型流式返回中提取有用文本，忽略纯“connected”等信息。
     */
    private String extractText(JsonNode node, String raw) {
        if (node == null) {
            return raw;
        }
        // 流式 chunk 内容
        if ("chunk".equalsIgnoreCase(node.path("type").asText()) && node.hasNonNull("content")) {
            return node.get("content").asText();
        }
        if (node.hasNonNull("text")) {
            return node.get("text").asText();
        }
        JsonNode message = node.get("message");
        if (message != null && message.isTextual()) {
            String msg = message.asText();
            if (!"connected".equalsIgnoreCase(msg)) {
                return msg;
            }
        }
        // content.message / content.text
        // content 可以是纯文本或对象
        JsonNode content = node.get("content");
        if (content != null) {
            if (content.isTextual()) {
                String msg = content.asText();
                if (!"connected".equalsIgnoreCase(msg)) {
                    return msg;
                }
            } else if (content.isObject()) {
                JsonNode contentMsg = content.get("message");
                if (contentMsg != null && contentMsg.isTextual() && !"connected".equalsIgnoreCase(contentMsg.asText())) {
                    return contentMsg.asText();
                }
                JsonNode contentText = content.get("text");
                if (contentText != null && contentText.isTextual()) {
                    return contentText.asText();
                }
                JsonNode choices = content.at("/choices/0/text");
                if (!choices.isMissingNode() && !choices.isNull()) {
                    return choices.asText();
                }
            }
        }
        JsonNode choices = node.at("/choices/0/text");
        if (!choices.isMissingNode() && !choices.isNull()) {
            return choices.asText();
        }
        return raw;
    }

    private void webSocketCloseSilently(WebSocket webSocket) {
        try {
            if (webSocket != null) {
                webSocket.close(1000, "done");
            }
        } catch (Exception ignore) {
        }
    }
}

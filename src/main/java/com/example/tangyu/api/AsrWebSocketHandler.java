package com.example.tangyu.api;

import com.example.tangyu.robot.RobotClient;
import com.example.tangyu.speech.AsrClient;
import com.example.tangyu.speech.AsrResultHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class AsrWebSocketHandler extends BinaryWebSocketHandler {
    private static final Logger LOG = LoggerFactory.getLogger(AsrWebSocketHandler.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AsrClient asrClient;
    private final RobotClient robotClient;

    public AsrWebSocketHandler(AsrClient asrClient, RobotClient robotClient) {
        this.asrClient = asrClient;
        this.robotClient = robotClient;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        LOG.info("ASR WS connected, id={}", session.getId());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        SessionContext ctx = getOrInitContext(session);
        if (!ctx.started.get()) {
            startRecognition(session, ctx);
        }
        if (ctx.recognition != null) {
            ctx.recognition.sendAudioFrame(ByteBuffer.wrap(message.getPayload().array()));
        }
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 客户端可以发送 "close" 表示结束
        if ("close".equalsIgnoreCase(message.getPayload())) {
            try {
                session.close(CloseStatus.NORMAL);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SessionContext ctx = (SessionContext) session.getAttributes().remove("asrCtx");
        if (ctx != null && ctx.recognition != null) {
            try {
                ctx.recognition.stop();
            } catch (Exception ignore) {
            }
            ctx.recognition = null;
        }
        LOG.info("ASR WS closed, id={}, status={}", session.getId(), status);
    }

    private SessionContext getOrInitContext(WebSocketSession session) {
        return (SessionContext) session.getAttributes().computeIfAbsent("asrCtx", k -> new SessionContext(session));
    }

    private void startRecognition(WebSocketSession session, SessionContext ctx) throws Exception {
        Map<String, List<String>> params = UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams();
        String format = first(params.get("format"), "pcm");
        int sampleRate = parseInt(first(params.get("sampleRate"), "16000"), 16000);
        String token = first(params.get("token"), null);
        ctx.token = token;

        AsrResultHandler handler = new AsrResultHandler();
        handler.setOnPartialResult(text -> sendJson(session, jsonMessage("partial", text, null)));
        handler.setOnFinalResult(text -> {
            // 最终结果调用大模型后再返回，避免分拆两条消息
            CompletableFuture.supplyAsync(() -> {
                if (text == null || text.trim().isEmpty()) {
                    LOG.warn("最终文本为空，跳过大模型调用");
                    return null;
                }
                if (ctx.token == null || ctx.token.isBlank()) {
                    LOG.warn("token 为空，跳过大模型调用");
                    return null;
                }
                try {
                    return robotClient.sendAndReceive(text, ctx.token);
                } catch (Exception e) {
                    LOG.error("调用大模型接口失败", e);
                    return null;
                }
            }).thenAccept(robotReply -> {
                if (robotReply != null && !robotReply.trim().isEmpty()) {
                    LOG.info("大模型返回结果: {}", robotReply);
                } else {
                    LOG.warn("大模型返回结果为空");
                }
                sendJson(session, jsonMessage("final", text, robotReply));
            });
        });

        ctx.recognition = asrClient.startStreaming(format, sampleRate, handler);
        ctx.started.set(true);
        ctx.handler = handler;
    }

    private void sendJson(WebSocketSession session, ObjectNode node) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(OBJECT_MAPPER.writeValueAsString(node)));
            }
        } catch (IOException e) {
            LOG.warn("Failed to send WS message", e);
        }
    }

    private ObjectNode jsonMessage(String type, String text, String robotReply) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("type", type);
        node.put("text", text);
        if (robotReply != null) {
            node.put("robotReply", robotReply);
        }
        return node;
    }

    private static String first(List<String> values, String defaultVal) {
        if (values == null || values.isEmpty()) {
            return defaultVal;
        }
        String v = values.get(0);
        return (v == null || v.isBlank()) ? defaultVal : v;
    }

    private static int parseInt(String s, int defaultVal) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return defaultVal;
        }
    }

    private static class SessionContext {
        final WebSocketSession session;
        com.alibaba.dashscope.audio.asr.recognition.Recognition recognition;
        AsrResultHandler handler;
        String token;
        AtomicBoolean started = new AtomicBoolean(false);

        SessionContext(WebSocketSession session) {
            this.session = session;
        }
    }
}

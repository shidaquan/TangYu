package com.example.tangyu.speech;

import com.example.tangyu.config.DashScopeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;

/**
 * ASR会话使用示例，参照Python WebSocket服务器的使用方式。
 */
public class AsrSessionExample {
    private static final Logger LOG = LoggerFactory.getLogger(AsrSessionExample.class);

    public static void main(String[] args) {
        // 1. 初始化配置
        DashScopeConfig config = DashScopeConfig.fromEnvironment();
        AsrClient asrClient = new AsrClient(config);

        // 2. 创建会话管理器
        AsrSessionManager sessionManager = new AsrSessionManager(asrClient, "pcm", 16000);

        // 3. 创建会话
        String sessionId = "session-" + System.currentTimeMillis();
        AsrSession session = sessionManager.getOrCreateSession(sessionId);

        // 4. 设置回调
        session.onPartialResult(text -> {
            System.out.println("部分结果: " + text);
        });

        session.onFinalResult(text -> {
            System.out.println("最终结果: " + text);
        });

        session.onProgress(progress -> {
            System.out.println("进度: " + progress);
        });

        session.onError(() -> {
            System.err.println("识别出错");
        });

        // 5. 开始识别（正常模式）
        session.start("normal");

        // 6. 模拟发送音频数据
        byte[] audioChunk = new byte[3200]; // 100ms的音频数据（16kHz, 16bit）
        // 这里应该是实际的音频数据
        // audioChunk = getAudioFromSource();

        // 发送多个音频块
        for (int i = 0; i < 20; i++) {
            session.addAudio(audioChunk, 0.5, "normal");
            try {
                Thread.sleep(100); // 模拟实时音频流
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // 7. 结束识别
        session.end();

        // 8. 清理
        sessionManager.removeSession(sessionId);

        // ========== KWS模式示例 ==========
        System.out.println("\n=== KWS模式示例 ===");
        String kwsSessionId = "kws-session-" + System.currentTimeMillis();
        AsrSession kwsSession = sessionManager.getOrCreateSession(kwsSessionId);

        kwsSession.onPartialResult(text -> {
            if (!text.isEmpty() && !text.equals("(音频太短)") && !text.equals("(无法识别)") && !text.equals("识别错误")) {
                System.out.println("KWS结果: " + text);
            }
        });

        // 开始KWS模式
        kwsSession.start("kws");

        // 发送KWS音频数据
        for (int i = 0; i < 50; i++) {
            kwsSession.addAudio(audioChunk, 0.5, "kws");
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        kwsSession.stop();
        sessionManager.removeSession(kwsSessionId);
        sessionManager.clearAll();
    }

    /**
     * 处理Base64编码的音频数据（参照Python逻辑）
     */
    public static void handleBase64Audio(AsrSession session, String base64Audio, double rms, String mode) {
        try {
            byte[] audioData = Base64.getDecoder().decode(base64Audio);
            session.addAudio(audioData, rms, mode);
        } catch (Exception e) {
            LOG.error("解码Base64音频失败", e);
        }
    }
}


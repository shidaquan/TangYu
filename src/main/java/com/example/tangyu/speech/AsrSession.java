package com.example.tangyu.speech;

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * ASR识别会话，管理音频缓冲和识别状态。
 * 参照Python WebSocket服务器的逻辑实现。
 */
public class AsrSession {
    private static final Logger LOG = LoggerFactory.getLogger(AsrSession.class);

    // 音频参数
    private static final int BYTES_PER_SAMPLE = 2; // 16bit = 2 bytes
    private static final double MIN_AUDIO_DURATION = 0.3; // 最小音频时长（秒）

    // KWS模式参数
    private static final int KWS_RECOGNIZE_INTERVAL = 8; // 每8个块识别一次（约0.4秒）
    private static final double KWS_KEEP_DURATION = 0.5; // 保留最后0.5秒用于连续性

    private final String sessionId;
    private final AsrClient asrClient;
    private final String format;
    private final int sampleRate;

    // 音频缓冲
    private byte[] audioBuffer = new byte[0];
    private int chunkCount = 0;

    // KWS模式缓冲
    private byte[] kwsBuffer = new byte[0];
    private int kwsChunkCount = 0;
    private boolean kwsMode = false;

    // 识别状态
    private Recognition recognition;
    private final AtomicBoolean isActive = new AtomicBoolean(false);
    private final AtomicBoolean isAborted = new AtomicBoolean(false);

    // 回调函数
    private Consumer<String> onPartialResult; // partial结果回调
    private Consumer<String> onFinalResult;   // final结果回调
    private Consumer<String> onProgress;      // 进度回调
    private Runnable onError;                 // 错误回调

    /**
     * 创建ASR会话
     *
     * @param sessionId  会话ID
     * @param asrClient  ASR客户端
     * @param format     音频格式
     * @param sampleRate 采样率
     */
    public AsrSession(String sessionId, AsrClient asrClient, String format, int sampleRate) {
        this.sessionId = sessionId;
        this.asrClient = asrClient;
        this.format = format;
        this.sampleRate = sampleRate;
    }

    /**
     * 设置回调函数
     */
    public AsrSession onPartialResult(Consumer<String> callback) {
        this.onPartialResult = callback;
        return this;
    }

    public AsrSession onFinalResult(Consumer<String> callback) {
        this.onFinalResult = callback;
        return this;
    }

    public AsrSession onProgress(Consumer<String> callback) {
        this.onProgress = callback;
        return this;
    }

    public AsrSession onError(Runnable callback) {
        this.onError = callback;
        return this;
    }

    /**
     * 开始新的识别会话
     *
     * @param mode 模式："kws" 或 "normal"
     */
    public void start(String mode) {
        if (isActive.get()) {
            LOG.warn("[{}] 会话已在进行中，先停止", sessionId);
            stop();
        }

        isAborted.set(false);
        audioBuffer = new byte[0];
        chunkCount = 0;
        kwsBuffer = new byte[0];
        kwsChunkCount = 0;
        kwsMode = "kws".equalsIgnoreCase(mode);

        if (kwsMode) {
            LOG.info("[{}] 开始 KWS 模式", sessionId);
        } else {
            LOG.info("[{}] 开始新句子识别", sessionId);
        }

        // 创建结果处理器
        AsrResultHandler handler = new AsrResultHandler();
        handler.setOnPartialResult(text -> {
            if (onPartialResult != null && text != null && !text.trim().isEmpty()) {
                onPartialResult.accept(text);
            }
        });
        handler.setOnFinalResult(text -> {
            if (onFinalResult != null && text != null && !text.trim().isEmpty()) {
                onFinalResult.accept(text);
            }
        });

        try {
            recognition = asrClient.startStreaming(format, sampleRate, handler);
            isActive.set(true);
        } catch (Exception e) {
            LOG.error("[{}] 启动识别失败", sessionId, e);
            if (onError != null) {
                onError.run();
            }
        }
    }

    /**
     * 添加音频数据
     *
     * @param audioData 音频数据
     * @param rms       RMS值（可选）
     * @param mode      模式（可选）
     */
    public void addAudio(byte[] audioData, double rms, String mode) {
        if (!isActive.get() || isAborted.get()) {
            return;
        }

        if (audioData == null || audioData.length == 0) {
            return;
        }

        boolean isKws = kwsMode || "kws".equalsIgnoreCase(mode);

        if (isKws) {
            // KWS模式：累积音频并周期性识别
            kwsBuffer = appendBytes(kwsBuffer, audioData);
            kwsChunkCount++;

            // 每 KWS_RECOGNIZE_INTERVAL 个块进行一次识别
            if (kwsChunkCount >= KWS_RECOGNIZE_INTERVAL) {
                double duration = calculateDuration(kwsBuffer);
                LOG.debug("[{}] KWS 识别: {:.2f}秒", sessionId, duration);

                if (duration >= MIN_AUDIO_DURATION && recognition != null) {
                    // 发送音频数据，识别结果会通过handler回调返回
                    sendAudioFrame(kwsBuffer);
                }

                // 重置 KWS 缓冲（保留最后 KWS_KEEP_DURATION 秒用于连续性）
                int keepBytes = (int) (KWS_KEEP_DURATION * sampleRate * BYTES_PER_SAMPLE);
                if (kwsBuffer.length > keepBytes) {
                    byte[] keep = new byte[keepBytes];
                    System.arraycopy(kwsBuffer, kwsBuffer.length - keepBytes, keep, 0, keepBytes);
                    kwsBuffer = keep;
                } else {
                    kwsBuffer = new byte[0];
                }
                kwsChunkCount = 0;
            }
        } else {
            // 正常ASR模式：持续发送音频数据
            audioBuffer = appendBytes(audioBuffer, audioData);
            chunkCount++;

            // 实时发送音频数据
            if (recognition != null) {
                sendAudioFrame(audioData);

                // 每10个块发送进度
                if (chunkCount % 10 == 0 && onProgress != null) {
                    double duration = calculateDuration(audioBuffer);
                    onProgress.accept(String.format("(录音中: %.1f秒)", duration));
                }
            }
        }
    }

    /**
     * 结束当前句子识别
     */
    public void end() {
        if (!isActive.get() || isAborted.get()) {
            return;
        }

        kwsMode = false;
        double duration = calculateDuration(audioBuffer);
        LOG.info("[{}] 句子结束, 音频: {} bytes ({:.2f}秒)", sessionId, audioBuffer.length, duration);

        if (duration >= MIN_AUDIO_DURATION) {
            // 正常模式下，音频已经实时发送，这里只需要停止识别触发final结果
            if (recognition != null) {
                asrClient.stopStreaming(recognition);
            }
        } else {
            LOG.info("[{}] 音频太短，跳过", sessionId);
            if (onFinalResult != null) {
                onFinalResult.accept("");
            }
            stop();
        }

        audioBuffer = new byte[0];
        chunkCount = 0;
    }

    /**
     * 打断当前识别
     */
    public void abort() {
        LOG.info("[{}] 收到打断指令", sessionId);
        isAborted.set(true);
        audioBuffer = new byte[0];
        chunkCount = 0;
        kwsBuffer = new byte[0];
        kwsChunkCount = 0;
        kwsMode = false;
    }

    /**
     * 停止会话
     */
    public void stop() {
        if (recognition != null) {
            try {
                asrClient.stopStreaming(recognition);
            } catch (Exception e) {
                LOG.warn("[{}] 停止识别时出错", sessionId, e);
            }
            recognition = null;
        }
        isActive.set(false);
        audioBuffer = new byte[0];
        kwsBuffer = new byte[0];
    }

    /**
     * 发送音频帧
     *
     * @param audioData 音频数据
     */
    private void sendAudioFrame(byte[] audioData) {
        if (recognition == null || !isActive.get() || audioData == null || audioData.length == 0) {
            return;
        }

        try {
            asrClient.sendAudioFrame(recognition, audioData, 0, audioData.length);
        } catch (Exception e) {
            LOG.error("[{}] 发送音频帧失败", sessionId, e);
            if (onError != null) {
                onError.run();
            }
        }
    }

    /**
     * 计算音频时长（秒）
     */
    private double calculateDuration(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            return 0.0;
        }
        return (double) audioData.length / (sampleRate * BYTES_PER_SAMPLE);
    }

    /**
     * 追加字节数组
     */
    private byte[] appendBytes(byte[] array1, byte[] array2) {
        byte[] result = new byte[array1.length + array2.length];
        System.arraycopy(array1, 0, result, 0, array1.length);
        System.arraycopy(array2, 0, result, array1.length, array2.length);
        return result;
    }

    public String getSessionId() {
        return sessionId;
    }

    public boolean isActive() {
        return isActive.get();
    }
}


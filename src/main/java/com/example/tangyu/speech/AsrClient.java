package com.example.tangyu.speech;

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionResult;
import com.alibaba.dashscope.common.ResultCallback;
import com.alibaba.dashscope.utils.Constants;
import com.example.tangyu.config.DashScopeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Fun-ASR Java SDK 的封装，支持同步和流式识别。
 */
public class AsrClient {
    private static final Logger LOG = LoggerFactory.getLogger(AsrClient.class);

    private final DashScopeConfig dashScopeConfig;

    public AsrClient(DashScopeConfig dashScopeConfig) {
        this.dashScopeConfig = Objects.requireNonNull(dashScopeConfig);
    }

    /**
     * 同步识别音频文件，返回完整结果。
     *
     * @param audioFile  待识别的音频文件
     * @param format     音频格式（如 pcm、wav）
     * @param sampleRate 采样率（如 16000）
     * @return 识别结果 JSON 字符串
     */
    public String transcribe(Path audioFile, String format, int sampleRate) {
        validate(audioFile, format, sampleRate);
        Recognition recognition = new Recognition();
        try {
            Constants.baseWebsocketApiUrl = dashScopeConfig.getBaseWebsocketUrl();
            RecognitionParam param = buildRecognitionParam(format, sampleRate);
            
            File file = audioFile.toFile();
            LOG.info("Starting ASR transcription for file: {}, format: {}, sampleRate: {}", 
                    file.getName(), format, sampleRate);
            
            String result = recognition.call(param, file);
            
            LOG.info("ASR transcription completed. requestId={}, firstPackageDelay={}ms, lastPackageDelay={}ms",
                    recognition.getLastRequestId(), 
                    recognition.getFirstPackageDelay(), 
                    recognition.getLastPackageDelay());
            return result;
        } catch (Exception e) {
            LOG.error("Failed to transcribe audio file: {}", audioFile, e);
            throw new RuntimeException("Failed to transcribe audio with Fun-ASR: " + e.getMessage(), e);
        } finally {
            closeRecognition(recognition);
        }
    }

    /**
     * 使用提供的处理器进行流式识别，便于拿到去重后的最终文本。
     */
    public String transcribeWithHandler(Path audioFile, String format, int sampleRate, AsrResultHandler handler) {
        Objects.requireNonNull(handler, "AsrResultHandler is required");
        validate(audioFile, format, sampleRate);
        Recognition recognition = new Recognition();
        try {
            Constants.baseWebsocketApiUrl = dashScopeConfig.getBaseWebsocketUrl();
            RecognitionParam param = buildRecognitionParam(format, sampleRate);

            long fileSize = Files.size(audioFile);
            LOG.info("Starting streaming ASR with handler for file: {}, format: {}, sampleRate: {}, fileSize: {} bytes",
                    audioFile.getFileName(), format, sampleRate, fileSize);
            
            recognition.call(param, handler);

            byte[] data = Files.readAllBytes(audioFile);
            LOG.info("读取音频文件完成，大小: {} bytes，开始发送音频数据", data.length);
            
            // 分块发送音频数据（每块16KB），避免一次性发送过大
            int chunkSize = 16 * 1024; // 16KB
            int offset = 0;
            while (offset < data.length) {
                int length = Math.min(chunkSize, data.length - offset);
                ByteBuffer chunk = ByteBuffer.wrap(data, offset, length);
                recognition.sendAudioFrame(chunk);
                offset += length;
                LOG.debug("已发送音频数据: {}/{} bytes", offset, data.length);
            }
            
            LOG.info("音频数据发送完成，开始停止识别");
            recognition.stop();

            // 等待结果完成（等待时间根据文件大小计算，最少15秒，最多30秒）
            long waitTime = Math.max(15_000, Math.min(30_000, fileSize / 100)); // 每100字节等待1ms，最少15秒
            long deadline = System.currentTimeMillis() + waitTime;
            LOG.info("等待识别结果完成，最多等待: {} ms", waitTime);
            
            int checkCount = 0;
            while (!handler.isCompleted() && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(100);
                    checkCount++;
                    if (checkCount % 50 == 0) { // 每5秒打印一次状态
                        String currentText = handler.getFullText();
                        LOG.info("等待中... 已等待: {} ms, 当前识别文本: {}", 
                                (System.currentTimeMillis() - (deadline - waitTime)), currentText);
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    LOG.warn("等待被中断");
                    break;
                }
            }

            boolean completed = handler.isCompleted();
            String finalText = handler.getFullText();
            
            LOG.info("Streaming ASR completed. requestId={}, firstPackageDelay={}ms, lastPackageDelay={}ms, " +
                            "handlerCompleted={}, finalTextLength={}",
                    recognition.getLastRequestId(),
                    recognition.getFirstPackageDelay(),
                    recognition.getLastPackageDelay(),
                    completed,
                    finalText != null ? finalText.length() : 0);
            
            if (!completed) {
                LOG.warn("识别可能未完全完成，但已超时。当前文本: {}", finalText);
            }
            
            if (finalText == null || finalText.trim().isEmpty()) {
                LOG.warn("识别结果为空！可能原因：1) 音频格式/采样率不匹配 2) 音频内容为空或太短 3) 识别服务异常");
            }
            
            return finalText != null ? finalText : "";
        } catch (Exception e) {
            LOG.error("Failed to transcribe with handler: {}", audioFile, e);
            throw new RuntimeException("Failed to transcribe audio with handler: " + e.getMessage(), e);
        } finally {
            closeRecognition(recognition);
        }
    }

    /**
     * 启动流式识别（适合麦克风等实时音频），实时回调结果。
=======
     * Streaming recognition with callback for real-time results.
     * Use this for real-time audio streams (e.g., microphone input).
>>>>>>> theirs
     *
     * @param format     音频格式（如 pcm）
     * @param sampleRate 采样率（如 16000）
     * @param callback   结果回调
     * @return 用于发送音频帧的 Recognition 实例
     */
    public Recognition startStreaming(String format, int sampleRate, ResultCallback<RecognitionResult> callback) {
        validateStreaming(format, sampleRate);
        Recognition recognition = new Recognition();
        try {
            Constants.baseWebsocketApiUrl = dashScopeConfig.getBaseWebsocketUrl();
            RecognitionParam param = buildRecognitionParam(format, sampleRate);
            
            LOG.info("Starting streaming ASR. format: {}, sampleRate: {}", format, sampleRate);
            recognition.call(param, callback);
            return recognition;
        } catch (Exception e) {
            LOG.error("Failed to start streaming ASR", e);
            closeRecognition(recognition);
            throw new RuntimeException("Failed to start streaming ASR: " + e.getMessage(), e);
        }
    }

    /**
     * 发送一帧音频数据到流式识别。
     *
     * @param recognition startStreaming 返回的实例
     * @param audioData   音频数据
     * @param offset      数据偏移
     * @param length      数据长度
     */
    public void sendAudioFrame(Recognition recognition, byte[] audioData, int offset, int length) {
        if (recognition == null) {
            throw new IllegalArgumentException("Recognition instance cannot be null");
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(audioData, offset, length);
            recognition.sendAudioFrame(buffer);
        } catch (Exception e) {
            LOG.error("Failed to send audio frame", e);
            throw new RuntimeException("Failed to send audio frame: " + e.getMessage(), e);
        }
    }

    /**
     * 停止流式识别并关闭连接。
     *
     * @param recognition Recognition 实例
     */
    public void stopStreaming(Recognition recognition) {
        if (recognition != null) {
            try {
                recognition.stop();
                LOG.info("Streaming ASR stopped. requestId={}, firstPackageDelay={}ms, lastPackageDelay={}ms",
                        recognition.getLastRequestId(),
                        recognition.getFirstPackageDelay(),
                        recognition.getLastPackageDelay());
            } catch (Exception e) {
                LOG.warn("Error stopping recognition", e);
            } finally {
                closeRecognition(recognition);
            }
        }
    }

    private RecognitionParam buildRecognitionParam(String format, int sampleRate) {
        return RecognitionParam.builder()
                .model(dashScopeConfig.getModel())
                .apiKey(dashScopeConfig.getApiKey())
                .format(format)
                .sampleRate(sampleRate)
                .parameter("language_hints", dashScopeConfig.getLanguageHints())
                .build();
    }

    private void closeRecognition(Recognition recognition) {
        if (recognition != null && recognition.getDuplexApi() != null) {
            try {
                recognition.getDuplexApi().close(1000, "bye");
            } catch (Exception e) {
                LOG.warn("Error closing recognition connection", e);
            }
        }
    }

    private void validate(Path audioFile, String format, int sampleRate) {
        if (audioFile == null || !Files.isReadable(audioFile)) {
            throw new IllegalArgumentException("Audio file must exist and be readable: " + audioFile);
        }
        if (format == null || format.isBlank()) {
            throw new IllegalArgumentException("Audio format must be provided (e.g., pcm, wav)");
        }
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("Sample rate must be positive, got: " + sampleRate);
        }
    }

    private void validateStreaming(String format, int sampleRate) {
        if (format == null || format.isBlank()) {
            throw new IllegalArgumentException("Audio format must be provided (e.g., pcm)");
        }
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("Sample rate must be positive, got: " + sampleRate);
        }
    }
}

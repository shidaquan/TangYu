package com.example.tangyu.speech;

import com.alibaba.dashscope.audio.asr.recognition.RecognitionResult;
import com.alibaba.dashscope.common.ResultCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 流式 ASR 结果回调处理器，负责 onEvent/onError/onComplete。
 * 支持部分结果与最终结果，做文本去重；通过反射兼容不同 SDK 版本。
 */
public class AsrResultHandler extends ResultCallback<RecognitionResult> {
    private static final Logger LOG = LoggerFactory.getLogger(AsrResultHandler.class);

    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final AtomicBoolean finalResultTriggered = new AtomicBoolean(false);
    private final StringBuilder fullText = new StringBuilder();
    private final boolean enableDeduplication;

    // 回调函数
    private Consumer<String> onPartialResult;
    private Consumer<String> onFinalResult;

    /**
     * 创建结果处理器
     *
     * @param enableDeduplication 是否启用去重
     */
    public AsrResultHandler(boolean enableDeduplication) {
        this.enableDeduplication = enableDeduplication;
    }

    /**
     * 创建结果处理器（默认启用去重）
     */
    public AsrResultHandler() {
        this(true);
    }

    /**
     * 设置部分结果回调
     */
    public void setOnPartialResult(Consumer<String> callback) {
        this.onPartialResult = callback;
    }

    /**
     * 设置最终结果回调
     */
    public void setOnFinalResult(Consumer<String> callback) {
        this.onFinalResult = callback;
    }

    @Override
    public void onEvent(RecognitionResult result) {
        if (result != null) {
            LOG.debug("收到ASR识别结果: {}", result);
            String text = extractText(result);
            LOG.debug("提取的文本: {}", text);
            
            if (text != null && !text.trim().isEmpty()) {
                // 去重处理
                if (enableDeduplication) {
                    text = TextDeduplicator.deduplicate(text);
                }

                // 先累积文本，再根据标点判断是否可能为最终结果
                fullText.append(text);
                boolean isFinal = text.matches(".*[。！？.!?].*");

                if (isFinal) {
                    LOG.info("识别到最终结果（带标点），立即触发回调: {}", text);
                    if (onFinalResult != null) {
                        finalResultTriggered.set(true);
                        onFinalResult.accept(text);
                    }
                } else {
                    LOG.info("部分识别结果: {}", text);
                    if (onPartialResult != null) {
                        onPartialResult.accept(text);
                    }
                }
            } else {
                LOG.warn("提取的文本为空，RecognitionResult: {}", result);
            }
        } else {
            LOG.warn("收到空的RecognitionResult");
        }
    }

    @Override
    public void onError(Exception exception) {
        LOG.error("ASR recognition error", exception);
        System.err.println("识别错误: " + exception.getMessage());
        completed.set(true);
    }

    @Override
    public void onComplete() {
        LOG.info("ASR recognition completed");
        String finalText = getFullText();
        LOG.info("识别完成，累积文本: {}", finalText);
        
        // 如果之前已经触发过最终结果（通过onEvent中的标点检测），则不再重复触发
        // 只有在没有触发过的情况下，才在onComplete时触发（作为兜底，处理没有标点符号的情况）
        if (!finalText.isEmpty() && onFinalResult != null && !finalResultTriggered.get()) {
            LOG.info("识别完成但未检测到标点符号，触发最终结果回调: {}", finalText);
            // 最后一次去重
            if (enableDeduplication) {
                finalText = TextDeduplicator.deduplicate(finalText);
            }
            onFinalResult.accept(finalText);
        } else if (finalText.isEmpty()) {
            LOG.warn("识别完成但文本为空，可能没有收到任何识别结果");
        } else if (finalResultTriggered.get()) {
            LOG.debug("识别完成，但之前已通过onEvent触发过最终结果，跳过重复调用");
        }
        completed.set(true);
    }

    /**
     * 通过反射从 RecognitionResult 中提取文本，兼容不同 SDK 版本。
     */
    private String extractText(RecognitionResult result) {
        if (result == null) {
            return null;
        }

        // 尝试多种方法提取文本
        String[] methodsToTry = {"getText", "getSentence", "getSentenceText", "text", "sentence"};
        
        for (String methodName : methodsToTry) {
            try {
                Method method = result.getClass().getMethod(methodName);
                Object textObj = method.invoke(result);
                if (textObj != null) {
                    String text = textObj.toString().trim();
                    if (!text.isEmpty()) {
                        LOG.debug("通过方法 {} 提取到文本: {}", methodName, text);
                        return text;
                    }
                }
            } catch (NoSuchMethodException e) {
                // 方法不存在，继续尝试下一个
                LOG.trace("方法 {} 不存在", methodName);
            } catch (Exception e) {
                LOG.debug("调用方法 {} 时出错: {}", methodName, e.getMessage());
            }
        }

        // 尝试直接toString
        try {
            String str = result.toString();
            if (str != null && !str.trim().isEmpty() && !str.equals(result.getClass().getName() + "@" + Integer.toHexString(result.hashCode()))) {
                // 优先解析形如 "text=你好" 的片段
                int idx = str.indexOf("text=");
                if (idx >= 0) {
                    int end = str.indexOf(",", idx);
                    String textPart = end > idx ? str.substring(idx + 5, end) : str.substring(idx + 5);
                    textPart = textPart.trim();
                    if (!textPart.isEmpty()) {
                        LOG.debug("通过toString文本片段提取到 text: {}", textPart);
                        return textPart;
                    }
                }
                LOG.debug("通过toString提取到文本: {}", str);
                return str;
            }
        } catch (Exception e) {
            LOG.debug("toString方法失败: {}", e.getMessage());
        }

        LOG.warn("无法从RecognitionResult中提取文本，类型: {}", result.getClass().getName());
        return null;
    }

    /**
     * 获取当前累积的完整识别文本。
     *
     * @return 完整文本
     */
    public String getFullText() {
        String text = fullText.toString();
        if (enableDeduplication) {
            return TextDeduplicator.deduplicate(text);
        }
        return text;
    }

    /**
     * 判断识别是否完成（成功或失败）。
     *
     * @return true 表示已完成
     */
    public boolean isCompleted() {
        return completed.get();
    }

    /**
     * 重置处理器状态，便于复用。
     */
    public void reset() {
        fullText.setLength(0);
        completed.set(false);
        finalResultTriggered.set(false);
    }
}

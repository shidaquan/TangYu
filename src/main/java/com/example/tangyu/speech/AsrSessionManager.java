package com.example.tangyu.speech;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ASR会话管理器，管理多个识别会话。
 * 参照Python WebSocket服务器的多客户端管理逻辑。
 */
public class AsrSessionManager {
    private static final Logger LOG = LoggerFactory.getLogger(AsrSessionManager.class);

    private final Map<String, AsrSession> sessions = new ConcurrentHashMap<>();
    private final AsrClient asrClient;
    private final String format;
    private final int sampleRate;

    public AsrSessionManager(AsrClient asrClient, String format, int sampleRate) {
        this.asrClient = asrClient;
        this.format = format;
        this.sampleRate = sampleRate;
    }

    /**
     * 创建或获取会话
     *
     * @param sessionId 会话ID
     * @return ASR会话
     */
    public AsrSession getOrCreateSession(String sessionId) {
        return sessions.computeIfAbsent(sessionId, id -> {
            LOG.info("创建新会话: {}", id);
            return new AsrSession(id, asrClient, format, sampleRate);
        });
    }

    /**
     * 获取会话
     *
     * @param sessionId 会话ID
     * @return ASR会话，如果不存在返回null
     */
    public AsrSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * 移除会话
     *
     * @param sessionId 会话ID
     */
    public void removeSession(String sessionId) {
        AsrSession session = sessions.remove(sessionId);
        if (session != null) {
            LOG.info("移除会话: {}", sessionId);
            session.stop();
        }
    }

    /**
     * 清理所有会话
     */
    public void clearAll() {
        LOG.info("清理所有会话，共 {} 个", sessions.size());
        sessions.values().forEach(AsrSession::stop);
        sessions.clear();
    }

    /**
     * 获取活跃会话数量
     *
     * @return 活跃会话数
     */
    public int getActiveSessionCount() {
        return (int) sessions.values().stream()
                .filter(AsrSession::isActive)
                .count();
    }

    /**
     * 获取总会话数量
     *
     * @return 总会话数
     */
    public int getTotalSessionCount() {
        return sessions.size();
    }
}


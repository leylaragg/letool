package com.github.leyland.letool.cache.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Redis pub/sub 失效消息监听器。
 *
 * <p>该监听器只做一件事：把收到的失效消息路由到 {@link CacheManager}，清理当前 JVM 的 L1。
 * 它不会删除 Redis L2，也不会继续发布消息，避免形成消息回环。</p>
 *
 * <p>如果消息来源 instanceId 和当前 JVM 一致，说明这是自己刚刚发出的广播，可以直接忽略。
 * 当前 JVM 在写入/删除时已经同步清理过自己的 L1。</p>
 */
public class RedisCacheInvalidationListener {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheInvalidationListener.class);

    /** 缓存管理器负责找到对应缓存实例并执行本地 L1 清理。 */
    private final CacheManager cacheManager;

    public RedisCacheInvalidationListener(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * 处理 Redis pub/sub 收到的 payload。
     *
     * @param payload {@link CacheInvalidationMessage#toPayload()} 生成的字符串
     */
    public void onMessage(String payload) {
        try {
            CacheInvalidationMessage message = CacheInvalidationMessage.fromPayload(payload);
            if (cacheManager.instanceId().equals(message.getSourceInstanceId())) {
                // 忽略本 JVM 自己发出的消息，避免重复清理。
                return;
            }
            if (message.isAll()) {
                cacheManager.evictLocalAll(message.getCacheName());
            } else {
                for (String key : message.getKeys()) {
                    cacheManager.evictLocal(message.getCacheName(), key);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to handle cache invalidation payload [{}]", payload, e);
        }
    }
}

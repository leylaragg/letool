package com.github.leyland.letool.mq.provider;

import com.github.leyland.letool.mq.core.Message;
import com.github.leyland.letool.mq.core.MessageListener;
import com.github.leyland.letool.mq.core.MqProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 内存消息队列提供者 —— 基于 {@link ConcurrentHashMap} 的纯内存实现，无需任何外部 MQ 依赖.
 *
 * <h3>实现机制</h3>
 * <ul>
 *   <li>主题 → 监听器列表 的映射存储在 {@link ConcurrentHashMap} 中</li>
 *   <li>{@link #send(String, Message)} 直接同步遍历监听器并回调 {@link MessageListener#onMessage(Message)}</li>
 *   <li>支持延迟消息：通过 {@link ScheduledExecutorService} 定时投递</li>
 *   <li>线程安全：使用 {@link CopyOnWriteArrayList} 存储监听器列表</li>
 * </ul>
 *
 * <h3>适用场景</h3>
 * <ul>
 *   <li>开发/测试环境 —— 无需启动额外的 MQ 服务</li>
 *   <li>单元测试 —— 轻量级消息验证</li>
 *   <li>简单单机应用 —— 无需分布式消息投递</li>
 * </ul>
 *
 * <h3>局限性</h3>
 * <ul>
 *   <li>不支持持久化 —— 服务重启后消息丢失</li>
 *   <li>不支持分布式 —— 仅 JVM 内部通信</li>
 *   <li>不支持消息确认与重试机制</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 */
public class InMemoryMqProvider implements MqProvider {

    private static final Logger log = LoggerFactory.getLogger(InMemoryMqProvider.class);

    // ======================== 存储结构 ========================

    /** 主题 → 监听器列表映射（线程安全） */
    private final ConcurrentHashMap<String, List<MessageListener>> listeners = new ConcurrentHashMap<>();

    /** 延迟消息调度线程池 */
    private final ScheduledExecutorService delayExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "letool-mq-delay");
        t.setDaemon(true);
        return t;
    });

    // ======================== 消息发送 ========================

    /**
     * 发送消息 —— 遍历当前主题的所有监听器并同步回调.
     *
     * @param topic   消息主题
     * @param message 消息对象
     */
    @Override
    public void send(String topic, Message message) {
        send(topic, null, message);
    }

    /**
     * 发送带标签的消息 —— 支持延迟投递（通过 {@code X-DELAY-MILLIS} 头部识别）.
     *
     * @param topic   消息主题
     * @param tag     消息标签（预留字段，内存实现中暂不按标签过滤）
     * @param message 消息对象
     */
    @Override
    public void send(String topic, String tag, Message message) {
        // 检查是否有延迟投递需求
        String delayHeader = message.getHeader("X-DELAY-MILLIS");
        if (delayHeader != null) {
            long delayMs = Long.parseLong(delayHeader);
            log.debug("[letool-mq-memory] 延迟投递消息: topic={}, delay={}ms, messageId={}",
                    topic, delayMs, message.getMessageId());
            delayExecutor.schedule(() -> dispatch(topic, message), delayMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } else {
            dispatch(topic, message);
        }
    }

    /**
     * 实际投递消息到所有注册的监听器（同步调用）.
     *
     * @param topic   消息主题
     * @param message 消息对象
     */
    private void dispatch(String topic, Message message) {
        List<MessageListener> topicListeners = listeners.get(topic);
        if (topicListeners == null || topicListeners.isEmpty()) {
            log.debug("[letool-mq-memory] 主题 {} 无消费者，消息丢弃: messageId={}", topic, message.getMessageId());
            return;
        }
        log.debug("[letool-mq-memory] 分发消息: topic={}, listeners={}, messageId={}",
                topic, topicListeners.size(), message.getMessageId());
        for (MessageListener listener : topicListeners) {
            try {
                listener.onMessage(message);
            } catch (Exception e) {
                log.error("[letool-mq-memory] 消息处理异常: topic={}, messageId={}",
                        topic, message.getMessageId(), e);
            }
        }
    }

    // ======================== 消息订阅 ========================

    /**
     * 订阅指定主题 —— 将监听器注册到该主题的监听列表中.
     *
     * @param topic    消息主题
     * @param listener 消息监听器
     */
    @Override
    public void subscribe(String topic, MessageListener listener) {
        listeners.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(listener);
        log.info("[letool-mq-memory] 订阅成功: topic={}, 当前监听器数量={}",
                topic, listeners.get(topic).size());
    }

    /**
     * 取消订阅 —— 从该主题的监听列表中移除指定监听器.
     *
     * @param topic    消息主题
     * @param listener 消息监听器
     */
    @Override
    public void unsubscribe(String topic, MessageListener listener) {
        List<MessageListener> topicListeners = listeners.get(topic);
        if (topicListeners != null) {
            topicListeners.remove(listener);
            log.info("[letool-mq-memory] 取消订阅: topic={}, 剩余监听器数量={}",
                    topic, topicListeners.size());
            // 如果该主题没有监听器了，移除映射条目
            if (topicListeners.isEmpty()) {
                listeners.remove(topic);
            }
        }
    }

    // ======================== 查询方法 ========================

    /**
     * 获取指定主题的监听器数量（主要用于测试和监控）.
     *
     * @param topic 消息主题
     * @return 该主题已注册的监听器数量
     */
    public int getListenerCount(String topic) {
        List<MessageListener> topicListeners = listeners.get(topic);
        return topicListeners != null ? topicListeners.size() : 0;
    }

    /**
     * 获取所有已注册的主题数量.
     *
     * @return 已注册主题总数
     */
    public int getTopicCount() {
        return listeners.size();
    }
}

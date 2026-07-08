package com.github.leyland.letool.cache.core;

/**
 * L1 本地缓存失效消息发布器。
 *
 * <p>二级缓存的 L1 存在于每个 JVM 内存中，所以在多实例部署时，某个实例写入或删除缓存后，
 * 其他实例必须同步清理自己的 L1，否则可能继续命中旧值。该接口用于抽象消息发布能力，
 * 当前默认实现是 Redis Pub/Sub，也可以由业务系统替换为 MQ、事件总线或其他广播机制。</p>
 */
@FunctionalInterface
public interface CacheInvalidationPublisher {

    /**
     * 发布一条缓存失效消息。
     *
     * @param message 失效消息，包含缓存名称、key、操作类型、版本号和来源实例 ID
     */
    void publish(CacheInvalidationMessage message);

    /**
     * 返回一个空实现。
     *
     * <p>当没有配置 Redis 或业务系统关闭跨 JVM 失效通知时，缓存管理器会使用该实现，
     * 这样调用方无需额外判断发布器是否存在。</p>
     */
    static CacheInvalidationPublisher noop() {
        return message -> { };
    }
}

package io.github.leylaragg.letool.cache.consistency;

/**
 * 持久化缓存失效事件状态。
 */
public enum CacheInvalidationEventStatus {
    /** 等待首次处理或重试。 */
    PENDING,
    /** 已被某个实例领取。 */
    PROCESSING,
    /** 缓存失效已经完成。 */
    COMPLETED
}

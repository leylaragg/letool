package io.github.leylaragg.letool.cache.core;

/**
 * Redis 权威读取失败时的缓存返回策略。
 */
public enum CacheReadFailurePolicy {

    /** 无法确认数据新鲜度时抛出受控异常，适合规则索引等正确性优先场景。 */
    FAIL_CLOSED,
    /** 有本地快照时允许返回旧值，没有快照时返回空结果，兼容 2.1.x 行为。 */
    STALE_IF_AVAILABLE,
    /** Redis 故障时始终返回空结果，只适用于明确选择可用性优先的非关键场景。 */
    EMPTY_ON_FAILURE
}

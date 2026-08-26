package io.github.leylaragg.letool.cache.core;

/**
 * Redis L2 变更失败时的缓存处理策略。
 *
 * <p>该策略只描述缓存基础设施写入是否必须获得确认，不替代数据库事务或
 * {@code CacheWritePolicy} 的失效、更新选择。</p>
 */
public enum CacheWriteFailurePolicy {

    /** Redis 写入失败时保留兼容降级行为，优先保证业务可用。 */
    BEST_EFFORT,
    /** Redis 写入结果无法确认时抛出 CACHE_006，优先保证调用方可感知失败。 */
    FAIL_CLOSED
}

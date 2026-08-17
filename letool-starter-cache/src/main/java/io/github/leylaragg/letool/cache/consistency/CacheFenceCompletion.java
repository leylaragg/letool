package io.github.leylaragg.letool.cache.consistency;

/**
 * 写入围栏完成操作的结果。
 */
public enum CacheFenceCompletion {

    /** 当前事件成功删除旧缓存并解除围栏。 */
    COMPLETED,

    /** 当前事件已经处理过，本次为幂等重放。 */
    ALREADY_COMPLETED,

    /** 当前 Key 已经由其它写事务持有，不能解除其围栏。 */
    SUPERSEDED
}

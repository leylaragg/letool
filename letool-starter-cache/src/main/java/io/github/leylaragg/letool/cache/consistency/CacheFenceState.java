package io.github.leylaragg.letool.cache.consistency;

/**
 * 单个业务 Key 的写入围栏状态。
 */
public enum CacheFenceState {

    /** 当前没有写事务占用该业务 Key。 */
    CLEAR,

    /** 当前存在写事务，读取必须绕过缓存。 */
    FENCED,

    /** Redis 无法确认状态，读取必须按不可信处理。 */
    UNKNOWN
}

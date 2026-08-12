package com.github.leyland.letool.cache.consistency;

/**
 * L1 本地缓存命中时采用的读取校验策略。
 */
public enum CacheReadValidation {

    /**
     * 使用 Redis 中的一致性版本校验本地缓存条目。
     */
    VERSIONED,

    /**
     * 不执行远程校验，允许直接返回本地缓存条目。
     */
    NONE
}

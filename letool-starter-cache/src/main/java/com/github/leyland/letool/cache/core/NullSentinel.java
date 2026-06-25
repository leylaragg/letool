package com.github.leyland.letool.cache.core;

/**
 * 空值哨兵 —— 标记缓存中存储的是 null 值，防止缓存穿透.
 *
 * <p>当 loader 返回 null 且 {@code nullValueCache=true} 时，缓存中存储此哨兵对象
 * 而非真实 null（因为 Caffeine 不允许存储 null 值）。下次查询命中哨兵时直接返回 null，
 * 不穿透到 loader.</p>
 */
public final class NullSentinel {

    /** 单例 */
    static final NullSentinel INSTANCE = new NullSentinel();

    private NullSentinel() {}

    @Override
    public String toString() {
        return "NULL_SENTINEL";
    }
}

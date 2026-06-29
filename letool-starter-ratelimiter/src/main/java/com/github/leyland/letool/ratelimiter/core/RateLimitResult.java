package com.github.leyland.letool.ratelimiter.core;

/**
 * 限流结果 —— 不可变的值对象，封装限流判定结果。
 *
 * <p>每次调用 {@link RateLimiter#tryAcquire} 都会返回一个此类的实例，
 * 包含以下核心信息：</p>
 *
 * <ul>
 *   <li><b>allowed</b>：是否允许本次请求通过</li>
 *   <li><b>availablePermits</b>：当前剩余的可用许可数</li>
 *   <li><b>waitTimeMs</b>：预估需要等待的时间（毫秒），仅在被拒绝时有意义</li>
 * </ul>
 *
 * <p>推荐使用静态工厂方法创建实例，语义更清晰：</p>
 * <pre>{@code
 * RateLimitResult.allow(50);             // 允许通过，剩余 50 个许可
 * RateLimitResult.deny(2000);            // 拒绝，预估等待 2000ms
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 * @see RateLimiter
 * @see RateLimitTemplate
 */
public final class RateLimitResult {

    // ======================== 属性 ========================

    /** 是否允许本次请求通过 */
    private final boolean allowed;

    /** 当前剩余可用许可数（仅在允许时有效） */
    private final long availablePermits;

    /** 预估等待时间（毫秒），仅在被拒绝时有意义，允许时为 0 */
    private final long waitTimeMs;

    // ======================== 构造方法 ========================

    /**
     * 私有构造方法，通过静态工厂方法创建实例。
     *
     * @param allowed          是否允许
     * @param availablePermits 可用许可数
     * @param waitTimeMs       预估等待时间（毫秒）
     */
    private RateLimitResult(boolean allowed, long availablePermits, long waitTimeMs) {
        this.allowed = allowed;
        this.availablePermits = availablePermits;
        this.waitTimeMs = waitTimeMs;
    }

    // ======================== 静态工厂方法 ========================

    /**
     * 创建"允许通过"的结果。
     *
     * @param availablePermits 扣减后剩余的可用许可数
     * @return 允许通过的结果实例
     */
    public static RateLimitResult allow(long availablePermits) {
        return new RateLimitResult(true, availablePermits, 0);
    }

    /**
     * 创建"被拒绝"的结果。
     *
     * @param waitTimeMs 预估需要等待的时间（毫秒），直到有可用许可
     * @return 被拒绝的结果实例
     */
    public static RateLimitResult deny(long waitTimeMs) {
        return new RateLimitResult(false, 0, waitTimeMs);
    }

    // ======================== Getter ========================

    /**
     * 判断本次请求是否被允许通过。
     *
     * @return {@code true} 允许通过，{@code false} 被限流拒绝
     */
    public boolean isAllowed() {
        return allowed;
    }

    /**
     * 获取当前剩余可用许可数。
     *
     * <p>仅在被允许时（{@link #isAllowed()} == true）才有实际意义，
     * 被拒绝时返回 0。</p>
     *
     * @return 剩余可用许可数
     */
    public long getAvailablePermits() {
        return availablePermits;
    }

    /**
     * 获取预估等待时间（毫秒）。
     *
     * <p>仅在被拒绝时（{@link #isAllowed()} == false）才有实际意义，
     * 表示大约需要等待多久才能有可用许可。允许通过时返回 0。</p>
     *
     * @return 预估等待时间（毫秒）
     */
    public long getWaitTimeMs() {
        return waitTimeMs;
    }

    // ======================== toString ========================

    @Override
    public String toString() {
        return "RateLimitResult{" +
                "allowed=" + allowed +
                ", availablePermits=" + availablePermits +
                ", waitTimeMs=" + waitTimeMs +
                '}';
    }
}

package io.github.leylaragg.letool.ratelimiter.core;

/**
 * 不可变的限流判定结果。
 *
 * <p>Sentinel 不承诺返回精确的剩余许可和等待时间，因此本对象只暴露可以可靠获得的
 * 放行状态与阻断类型，避免向业务代码提供虚假精度。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public final class RateLimitResult {

    /**
     * 是否允许本次请求通过。
     */
    private final boolean allowed;

    /**
     * Sentinel 阻断异常类型；放行时为空。
     */
    private final String blockReason;

    /**
     * 创建限流结果。
     *
     * @param allowed     是否允许通过
     * @param blockReason 阻断原因
     */
    private RateLimitResult(boolean allowed, String blockReason) {
        this.allowed = allowed;
        this.blockReason = blockReason;
    }

    /**
     * 创建放行结果。
     *
     * @return 放行结果
     */
    public static RateLimitResult allowed() {
        return new RateLimitResult(true, null);
    }

    /**
     * 创建拒绝结果。
     *
     * @param blockReason Sentinel 阻断异常类型
     * @return 拒绝结果
     * @throws IllegalArgumentException 当阻断原因为空白时抛出
     */
    public static RateLimitResult rejected(String blockReason) {
        if (blockReason == null || blockReason.isBlank()) {
            throw new IllegalArgumentException("blockReason must not be blank");
        }
        return new RateLimitResult(false, blockReason);
    }

    /**
     * 判断请求是否被允许。
     *
     * @return 允许时返回 {@code true}
     */
    public boolean isAllowed() {
        return allowed;
    }

    /**
     * 获取 Sentinel 阻断异常类型。
     *
     * @return 阻断异常类型；放行时返回 {@code null}
     */
    public String getBlockReason() {
        return blockReason;
    }

    /**
     * 返回便于诊断的结果文本。
     *
     * @return 结果文本
     */
    @Override
    public String toString() {
        return "RateLimitResult{" +
                "allowed=" + allowed +
                ", blockReason='" + blockReason + '\'' +
                '}';
    }
}

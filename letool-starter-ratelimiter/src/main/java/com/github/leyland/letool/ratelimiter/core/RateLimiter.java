package com.github.leyland.letool.ratelimiter.core;

/**
 * 限流器核心接口 —— 定义限流算法的标准契约。
 *
 * <p>所有限流算法实现（令牌桶、滑动窗口、漏桶等）均需实现此接口。
 * 该接口抽象了三个核心操作：</p>
 *
 * <ul>
 *   <li><b>尝试获取许可</b> —— 检查指定 key 是否还有可用许可，有则扣减</li>
 *   <li><b>重置计数器</b> —— 重置指定 key 的限流状态</li>
 *   <li><b>查询可用许可</b> —— 查询指定 key 当前剩余的许可数</li>
 * </ul>
 *
 * <p>典型实现思路：</p>
 * <ul>
 *   <li>{@link com.github.leyland.letool.ratelimiter.algorithm.TokenBucketLimiter}：
 *       以恒定速率补充令牌，允许突发流量</li>
 *   <li>{@link com.github.leyland.letool.ratelimiter.algorithm.SlidingWindowLimiter}：
 *       统计时间窗口内请求数，更平滑的流量控制</li>
 * </ul>
 *
 * <p>更推荐使用 {@link RateLimitTemplate} 提供的模板方法 API，它能自动处理
 * 拒绝回退等逻辑，简化业务代码。</p>
 *
 * @author leyland
 * @since 2.0.0
 * @see RateLimitTemplate
 * @see RateLimitResult
 */
public interface RateLimiter {

    // ======================== 许可获取 ========================

    /**
     * 尝试获取指定数量的许可。
     *
     * <p>如果当前可用许可数 >= 请求的许可数，则扣减许可并返回允许；
     * 否则返回拒绝，并附带预估的等待时间。</p>
     *
     * @param key     限流的唯一标识（如 "sms:138xxxx"、"api:/order/create"）
     * @param permits 请求的许可数（通常为 1）
     * @return 限流结果，包含是否允许、可用许可数、预估等待时间
     */
    RateLimitResult tryAcquire(String key, int permits);

    // ======================== 状态管理 ========================

    /**
     * 重置指定 key 的限流状态。
     *
     * <p>清除该 key 的所有计数器/令牌数据，恢复到初始状态。
     * 常用于测试环境或手动解除限流。</p>
     *
     * @param key 限流的唯一标识
     */
    void reset(String key);

    // ======================== 状态查询 ========================

    /**
     * 获取指定 key 当前可用的许可数。
     *
     * <p>此操作为只读查询，不会扣减许可。用于监控或预检场景。</p>
     *
     * @param key 限流的唯一标识
     * @return 当前可用的许可数
     */
    long getAvailablePermits(String key);
}

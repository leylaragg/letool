package io.github.leylaragg.letool.ratelimiter.core;

/**
 * Letool 限流器扩展契约。
 *
 * <p>策略名用于定位一组稳定的限流规则，key 用于区分同一策略下的业务维度。
 * 默认实现委托 Alibaba Sentinel，业务项目仍可提供自定义 Bean 替换该实现。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
@FunctionalInterface
public interface RateLimiter {

    /**
     * 尝试获取指定数量的许可。
     *
     * @param policy  限流策略名称
     * @param key     策略下的动态业务 key；为空时执行全局限流
     * @param permits 本次请求消耗的许可数
     * @return 限流判定结果
     */
    RateLimitResult tryAcquire(String policy, String key, int permits);
}

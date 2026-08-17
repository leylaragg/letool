package io.github.leylaragg.letool.ratelimiter.sentinel;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import io.github.leylaragg.letool.ratelimiter.core.RateLimitResult;
import io.github.leylaragg.letool.ratelimiter.core.RateLimiter;
import io.github.leylaragg.letool.ratelimiter.exception.RateLimitConfigurationException;

/**
 * 基于 Alibaba Sentinel Core 的限流器实现。
 *
 * <p>没有 key 时使用普通 QPS 流控资源；提供 key 时将其作为 Sentinel 第 0 个热点参数，
 * 从而对同一策略下的每个业务 key 独立计数。算法、并发安全和统计窗口均由 Sentinel 维护。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public final class SentinelRateLimiter implements RateLimiter {

    /**
     * 尝试获取 Sentinel 许可。
     *
     * @param policy  限流策略名称
     * @param key     策略下的动态业务 key；为空时执行全局限流
     * @param permits 本次请求消耗的许可数
     * @return Sentinel 限流判定结果
     */
    @Override
    public RateLimitResult tryAcquire(String policy, String key, int permits) {
        String safePolicy = requirePolicy(policy);
        requirePermits(permits);

        try (Entry ignored = enter(safePolicy, key, permits)) {
            return RateLimitResult.allowed();
        } catch (BlockException exception) {
            return RateLimitResult.rejected(exception.getClass().getSimpleName());
        }
    }

    /**
     * 进入对应的 Sentinel 资源。
     *
     * @param policy  策略名称
     * @param key     动态业务 key
     * @param permits 许可数量
     * @return Sentinel 资源入口
     * @throws BlockException Sentinel 拒绝请求时抛出
     */
    private Entry enter(String policy, String key, int permits) throws BlockException {
        if (key == null || key.isBlank()) {
            return SphU.entry(
                    SentinelResourceNames.global(policy),
                    EntryType.IN,
                    permits
            );
        }
        return SphU.entry(
                SentinelResourceNames.keyed(policy),
                EntryType.IN,
                permits,
                key
        );
    }

    /**
     * 校验策略名称。
     *
     * @param policy 策略名称
     * @return 已校验策略名称
     */
    private String requirePolicy(String policy) {
        if (policy == null || policy.isBlank()) {
            throw RateLimitConfigurationException.invalid("policy");
        }
        return policy;
    }

    /**
     * 校验许可数量。
     *
     * @param permits 许可数量
     */
    private void requirePermits(int permits) {
        if (permits <= 0) {
            throw RateLimitConfigurationException.invalid("permits");
        }
    }
}

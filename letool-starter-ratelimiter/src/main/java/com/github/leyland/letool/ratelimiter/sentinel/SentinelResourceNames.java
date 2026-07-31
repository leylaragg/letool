package com.github.leyland.letool.ratelimiter.sentinel;

/**
 * Letool Sentinel 资源名称生成器。
 *
 * <p>该类型为模块内部约定，统一隔离全局策略与热点参数策略的资源名称。</p>
 */
final class SentinelResourceNames {

    /**
     * Letool 限流资源保留前缀。
     */
    static final String PREFIX = "letool:rate-limit:";

    /**
     * 工具类不允许实例化。
     */
    private SentinelResourceNames() {
    }

    /**
     * 生成全局限流资源名称。
     *
     * @param policy 策略名称
     * @return Sentinel 全局资源名称
     */
    static String global(String policy) {
        return PREFIX + policy + ":global";
    }

    /**
     * 生成热点参数限流资源名称。
     *
     * @param policy 策略名称
     * @return Sentinel 热点参数资源名称
     */
    static String keyed(String policy) {
        return PREFIX + policy + ":keyed";
    }

    /**
     * 判断资源是否由 Letool 本地规则管理。
     *
     * @param resource Sentinel 资源名称
     * @return 属于 Letool 保留命名空间时返回 {@code true}
     */
    static boolean isLetoolResource(String resource) {
        return resource != null && resource.startsWith(PREFIX);
    }
}

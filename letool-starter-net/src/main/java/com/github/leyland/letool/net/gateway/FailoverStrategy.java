package com.github.leyland.letool.net.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 故障转移策略处理器 —— 当服务器调用失败时，根据策略选择下一个可用节点.
 *
 * <h3>支持的策略（枚举 {@link Strategy}）</h3>
 * <ul>
 *   <li><b>RETRY_NEXT</b> —— 跳过失败节点，尝试下一个健康节点（默认）</li>
 *   <li><b>RETRY_SAME</b> —— 重试同一节点</li>
 *   <li><b>FAIL_FAST</b> —— 立即抛出异常，不重试</li>
 * </ul>
 *
 * <p>支持按服务器跟踪连续失败次数，达到阈值后自动标记为不健康.</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public class FailoverStrategy {

    private static final Logger log = LoggerFactory.getLogger(FailoverStrategy.class);

    // ======================== 枚举 ========================

    /**
     * 故障转移策略枚举.
     */
    public enum Strategy {
        /** 重试下一个健康节点 */
        RETRY_NEXT,
        /** 重试同一节点 */
        RETRY_SAME,
        /** 快速失败（不重试） */
        FAIL_FAST;

        /**
         * 从字符串解析策略.
         *
         * @param name 策略名称
         * @return 对应枚举值，未知时返回 RETRY_NEXT
         */
        public static Strategy fromName(String name) {
            if (name == null) {
                return RETRY_NEXT;
            }
            try {
                return Strategy.valueOf(name.toUpperCase().replace("-", "_"));
            } catch (IllegalArgumentException e) {
                log.warn("Unknown failover strategy '{}', fallback to RETRY_NEXT", name);
                return RETRY_NEXT;
            }
        }
    }

    // ======================== 字段 ========================

    /** 当前策略 */
    private final Strategy strategy;

    /** 最大重试次数 */
    private final int maxRetries;

    /** 连续失败计数器（host:port -> count） */
    private final Map<String, AtomicInteger> failureCounters = new ConcurrentHashMap<>();

    /** 连续失败阈值，超过则标记不健康 */
    private final int failureThreshold;

    // ======================== 构造器 ========================

    /**
     * 默认构造（RETRY_NEXT，最多重试 3 次）.
     */
    public FailoverStrategy() {
        this(Strategy.RETRY_NEXT, 3, 3);
    }

    /**
     * 全参构造.
     *
     * @param strategy         故障转移策略
     * @param maxRetries       最大重试次数
     * @param failureThreshold 连续失败阈值（超过则标记不健康）
     */
    public FailoverStrategy(Strategy strategy, int maxRetries, int failureThreshold) {
        this.strategy = strategy != null ? strategy : Strategy.RETRY_NEXT;
        this.maxRetries = Math.max(1, maxRetries);
        this.failureThreshold = Math.max(1, failureThreshold);
    }

    /**
     * 根据策略名称构造.
     *
     * @param strategyName     策略名称字符串
     * @param maxRetries       最大重试次数
     * @param failureThreshold 连续失败阈值
     */
    public FailoverStrategy(String strategyName, int maxRetries, int failureThreshold) {
        this(Strategy.fromName(strategyName), maxRetries, failureThreshold);
    }

    // ======================== 核心方法 ========================

    /**
     * 在失败后选择下一个要尝试的服务器.
     *
     * @param servers 所有服务器列表
     * @param failed  刚才失败的服务器（可为 null）
     * @return 下一个选中服务器，返回 null 表示全部失败
     */
    public BackendServer selectNext(List<BackendServer> servers, BackendServer failed) {
        if (servers == null || servers.isEmpty()) {
            return null;
        }

        // 记录失败
        if (failed != null) {
            recordFailure(failed);
        }

        switch (strategy) {
            case FAIL_FAST:
                return null; // 不重试

            case RETRY_SAME:
                // 重试同一节点（仅当节点仍标记为健康时）
                if (failed != null && failed.isHealthy()) {
                    return failed;
                }
                return findHealthy(servers, failed);

            case RETRY_NEXT:
            default:
                return findHealthy(servers, failed);
        }
    }

    /**
     * 在服务器列表中找到第一个健康的节点（跳过失败节点）.
     *
     * @param servers   服务器列表
     * @param failed    需要跳过的失败节点
     * @return 找到的健康节点，未找到返回 null
     */
    private BackendServer findHealthy(List<BackendServer> servers, BackendServer failed) {
        for (BackendServer server : servers) {
            // 跳过刚失败的节点
            if (failed != null && isSameServer(server, failed)) {
                continue;
            }
            if (server.isHealthy()) {
                return server;
            }
        }
        // 如果没有其他健康节点，尝试返回失败节点（最后一次机会）
        return null;
    }

    // ======================== 失败追踪 ========================

    /**
     * 记录服务器失败.
     *
     * @param server 失败的服务器
     */
    public void recordFailure(BackendServer server) {
        String key = server.getHost() + ":" + server.getPort();
        AtomicInteger counter = failureCounters.computeIfAbsent(key, k -> new AtomicInteger(0));
        int count = counter.incrementAndGet();
        if (count >= failureThreshold) {
            server.setHealthy(false);
            log.warn("Server {} marked unhealthy after {} consecutive failures", key, count);
        }
    }

    /**
     * 记录服务器成功，重置失败计数.
     *
     * @param server 成功的服务器
     */
    public void recordSuccess(BackendServer server) {
        String key = server.getHost() + ":" + server.getPort();
        failureCounters.remove(key);
    }

    /**
     * 获取服务器的连续失败次数.
     *
     * @param server 目标服务器
     * @return 连续失败次数
     */
    public int getFailureCount(BackendServer server) {
        String key = server.getHost() + ":" + server.getPort();
        AtomicInteger counter = failureCounters.get(key);
        return counter != null ? counter.get() : 0;
    }

    /**
     * 获取最大重试次数.
     *
     * @return 最大重试次数
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * 获取当前使用的策略.
     *
     * @return 故障转移策略枚举
     */
    public Strategy getStrategy() {
        return strategy;
    }

    // ======================== 内部方法 ========================

    /**
     * 判断两个服务器是否为同一节点（比较 host + port）.
     *
     * @param a 服务器 A
     * @param b 服务器 B
     * @return {@code true} 如果 host 和 port 都相同
     */
    private boolean isSameServer(BackendServer a, BackendServer b) {
        if (a == null || b == null) {
            return false;
        }
        return a.getHost().equals(b.getHost()) && a.getPort() == b.getPort();
    }
}

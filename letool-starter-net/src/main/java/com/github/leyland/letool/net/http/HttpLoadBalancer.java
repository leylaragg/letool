package com.github.leyland.letool.net.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HTTP 负载均衡器 —— 支持轮询（round-robin）和加权轮询（weighted round-robin）两种策略.
 *
 * <p>后端服务器列表使用 {@link CopyOnWriteArrayList} 保障读多写少的线程安全性.
 * 每次调用 {@link #next()} 自动跳过标记为不健康的节点.</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * HttpLoadBalancer lb = new HttpLoadBalancer();
 * lb.addServer(new BackendServer("192.168.1.1", 8080, 1));
 * lb.addServer(new BackendServer("192.168.1.2", 8080, 2));
 * BackendServer server = lb.next(); // 轮询选中
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class HttpLoadBalancer {

    private static final Logger log = LoggerFactory.getLogger(HttpLoadBalancer.class);

    // ======================== 字段 ========================

    /** 后端服务器列表（线程安全） */
    private final List<BackendServer> servers = new CopyOnWriteArrayList<>();

    /** 轮询当前索引 */
    private final AtomicInteger currentIndex = new AtomicInteger(0);

    /** 加权轮询当前索引 */
    private final AtomicInteger weightedIndex = new AtomicInteger(0);

    // ======================== 服务器管理 ========================

    /**
     * 添加后端服务器.
     *
     * @param server 后端服务器信息
     */
    public void addServer(BackendServer server) {
        if (server != null) {
            servers.add(server);
            log.info("Added backend server: {}://{}:{} (weight={})", server.getScheme(), server.getHost(), server.getPort(), server.getWeight());
        }
    }

    /**
     * 移除指定主机的后端服务器.
     *
     * @param host 要移除的服务器主机标识
     * @return {@code true} 如果找到并移除
     */
    public boolean removeServer(String host) {
        return servers.removeIf(s -> s.getHost().equals(host));
    }

    /**
     * 获取所有后端服务器.
     *
     * @return 服务器列表副本
     */
    public List<BackendServer> getServers() {
        return servers;
    }

    /**
     * 获取健康节点数量.
     *
     * @return 健康节点数
     */
    public int getHealthyCount() {
        int count = 0;
        for (BackendServer s : servers) {
            if (s.isHealthy()) {
                count++;
            }
        }
        return count;
    }

    // ======================== 轮询选择 ========================

    /**
     * 轮询选择下一个健康的后端服务器.
     *
     * <p>自动跳过不健康的节点，若所有节点都不健康则返回第一个.</p>
     *
     * @return 选中的后端服务器，若无可用节点返回 {@code null}
     */
    public BackendServer next() {
        if (servers.isEmpty()) {
            log.warn("No backend servers available");
            return null;
        }
        int size = servers.size();
        // 最多尝试所有节点
        for (int i = 0; i < size; i++) {
            // 使用 floorMod 而非 Math.abs(%)，避免 Integer.MIN_VALUE 溢出返回负数
            int idx = Math.floorMod(currentIndex.getAndIncrement(), size);
            BackendServer server = servers.get(idx);
            if (server.isHealthy()) {
                return server;
            }
        }
        // 全不健康，返回第一个作为降级
        log.warn("All backend servers are unhealthy, falling back to first");
        return servers.get(0);
    }

    /**
     * 加权轮询选择下一个健康的后端服务器.
     *
     * <p>权重越高被选中的概率越大。实现方式：按权重展开虚拟节点进行轮询.</p>
     *
     * @return 选中的后端服务器
     */
    public BackendServer nextWeighted() {
        if (servers.isEmpty()) {
            log.warn("No backend servers available");
            return null;
        }
        // 收集健康节点及权重
        int totalWeight = 0;
        for (BackendServer s : servers) {
            if (s.isHealthy()) {
                totalWeight += s.getWeight();
            }
        }
        if (totalWeight == 0) {
            log.warn("All servers unhealthy in weighted selection");
            return servers.get(0);
        }
        int idx = Math.floorMod(weightedIndex.getAndIncrement(), totalWeight);
        int cumulative = 0;
        for (BackendServer s : servers) {
            if (s.isHealthy()) {
                cumulative += s.getWeight();
                if (idx < cumulative) {
                    return s;
                }
            }
        }
        // 兜底
        return servers.get(servers.size() - 1);
    }

    // ======================== 内部类：BackendServer ========================

    /**
     * 后端服务器模型 —— 包含主机、端口、权重、健康状态、协议方案等信息.
     *
     * <p>使用 Builder 模式构建.</p>
     */
    public static class BackendServer {

        /** 主机名或 IP */
        private String host;

        /** 端口号 */
        private int port;

        /** 权重（用于加权负载均衡），默认 1 */
        private int weight;

        /** 是否健康 */
        private volatile boolean healthy;

        /** 协议方案（http/https/tcp） */
        private String scheme;

        /** 最后一次健康检查时间戳 */
        private long lastHealthCheckTime;

        // ---- 构造器 ----

        /**
         * 默认构造（localhost:80）.
         */
        public BackendServer() {
            this("localhost", 80, 1, true, "http");
        }

        /**
         * 全参构造.
         *
         * @param host   主机名
         * @param port   端口号
         * @param weight 权重
         * @param healthy 是否健康
         * @param scheme 协议方案
         */
        public BackendServer(String host, int port, int weight, boolean healthy, String scheme) {
            this.host = host;
            this.port = port;
            this.weight = weight;
            this.healthy = healthy;
            this.scheme = scheme != null ? scheme : "http";
        }

        // ---- Builder ----

        /**
         * 创建 Builder.
         *
         * @return Builder 实例
         */
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String host = "localhost";
            private int port = 80;
            private int weight = 1;
            private boolean healthy = true;
            private String scheme = "http";

            public Builder host(String host) { this.host = host; return this; }
            public Builder port(int port) { this.port = port; return this; }
            public Builder weight(int weight) { this.weight = weight; return this; }
            public Builder healthy(boolean healthy) { this.healthy = healthy; return this; }
            public Builder scheme(String scheme) { this.scheme = scheme; return this; }

            public BackendServer build() {
                return new BackendServer(host, port, weight, healthy, scheme);
            }
        }

        // ---- Getter / Setter ----

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public int getWeight() { return weight; }
        public void setWeight(int weight) { this.weight = weight; }
        public boolean isHealthy() { return healthy; }
        public void setHealthy(boolean healthy) { this.healthy = healthy; }
        public String getScheme() { return scheme; }
        public void setScheme(String scheme) { this.scheme = scheme; }
        public long getLastHealthCheckTime() { return lastHealthCheckTime; }
        public void setLastHealthCheckTime(long lastHealthCheckTime) { this.lastHealthCheckTime = lastHealthCheckTime; }

        @Override
        public String toString() {
            return scheme + "://" + host + ":" + port + "(w=" + weight + ", healthy=" + healthy + ")";
        }
    }
}

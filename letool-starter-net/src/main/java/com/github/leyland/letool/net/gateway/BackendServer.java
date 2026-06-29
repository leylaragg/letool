package com.github.leyland.letool.net.gateway;

/**
 * 后端服务器模型 —— 描述网关路由中一个后端服务器的完整信息.
 *
 * <p>包含主机、端口、权重、健康状态、协议方案、最后检查时间等字段.
 * 支持 Builder 模式构建.</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public class BackendServer {

    // ======================== 字段 ========================

    /** 主机名或 IP 地址 */
    private String host;

    /** 端口号 */
    private int port;

    /** 权重（用于加权负载均衡），默认 1 */
    private int weight;

    /** 是否健康 */
    private volatile boolean healthy;

    /** 协议方案（http/https/tcp） */
    private String scheme;

    /** 最后一次健康检查时间戳（毫秒） */
    private long lastHealthCheckTime;

    // ======================== 构造器 ========================

    /**
     * 默认构造，localhost:80.
     */
    public BackendServer() {
        this("localhost", 80, 1, true, "tcp");
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
        this.scheme = scheme != null ? scheme : "tcp";
        this.lastHealthCheckTime = System.currentTimeMillis();
    }

    // ======================== Builder ========================

    /**
     * 创建 Builder 实例.
     *
     * @return BackendServer 构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * BackendServer 的 Builder 模式构造器.
     */
    public static class Builder {
        private String host = "localhost";
        private int port = 80;
        private int weight = 1;
        private boolean healthy = true;
        private String scheme = "tcp";

        /**
         * 设置主机名.
         *
         * @param host 主机名或 IP
         * @return this
         */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /**
         * 设置端口号.
         *
         * @param port 端口
         * @return this
         */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /**
         * 设置权重.
         *
         * @param weight 权重值
         * @return this
         */
        public Builder weight(int weight) {
            this.weight = weight;
            return this;
        }

        /**
         * 设置健康状态.
         *
         * @param healthy 是否健康
         * @return this
         */
        public Builder healthy(boolean healthy) {
            this.healthy = healthy;
            return this;
        }

        /**
         * 设置协议方案.
         *
         * @param scheme 协议（http/https/tcp）
         * @return this
         */
        public Builder scheme(String scheme) {
            this.scheme = scheme;
            return this;
        }

        /**
         * 构建 BackendServer 实例.
         *
         * @return 服务器模型
         */
        public BackendServer build() {
            return new BackendServer(host, port, weight, healthy, scheme);
        }

        // ---- 便捷工厂方法 ----

        /**
         * 快速构建 HTTP 服务器节点.
         *
         * @param host 主机名
         * @param port 端口
         * @return 构建完毕的 Builder（调用方可继续设置其他属性后调 build()）
         */
        public static Builder http(String host, int port) {
            return new Builder().host(host).port(port).scheme("http");
        }

        /**
         * 快速构建 TCP 服务器节点.
         *
         * @param host 主机名
         * @param port 端口
         * @return 构建完毕的 Builder
         */
        public static Builder tcp(String host, int port) {
            return new Builder().host(host).port(port).scheme("tcp");
        }
    }

    // ======================== 便捷工厂 ========================

    /**
     * 快速创建 HTTP 后端服务器.
     *
     * @param host   主机名
     * @param port   端口
     * @param weight 权重
     * @return 服务器实例
     */
    public static BackendServer http(String host, int port, int weight) {
        return new BackendServer(host, port, weight, true, "http");
    }

    /**
     * 快速创建 TCP 后端服务器.
     *
     * @param host   主机名
     * @param port   端口
     * @param weight 权重
     * @return 服务器实例
     */
    public static BackendServer tcp(String host, int port, int weight) {
        return new BackendServer(host, port, weight, true, "tcp");
    }

    // ======================== Getter / Setter ========================

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

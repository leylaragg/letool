package com.github.leyland.letool.net.tcp;

/**
 * TCP 客户端配置模型 —— 聚合主机、端口、超时、Socket 选项、连接池等所有 TCP 连接参数.
 *
 * <p>使用 Builder 模式构建，所有参数均有合理的默认值：</p>
 * <pre>{@code
 * TcpConfig config = TcpConfig.builder()
 *         .host("192.168.1.100")
 *         .port(8088)
 *         .connectTimeoutMs(5000)
 *         .readTimeoutMs(30000)
 *         .noDelay(true)
 *         .build();
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class TcpConfig {

    // ======================== 字段 ========================

    /** 远程主机名或 IP，默认 "localhost" */
    private String host = "localhost";

    /** 远程端口，默认 8080 */
    private int port = 8080;

    /** 连接超时（毫秒），默认 10000 */
    private int connectTimeoutMs = 10_000;

    /** 读取超时（毫秒），默认 60000 */
    private int readTimeoutMs = 60_000;

    /** 是否禁用 Nagle 算法（TCP_NODELAY），默认 true */
    private boolean noDelay = true;

    /** 是否启用 KeepAlive（SO_KEEPALIVE），默认 false */
    private boolean keepAlive = false;

    /** 连接池最小空闲连接数，默认 2 */
    private int poolMinIdle = 2;

    /** 连接池最大连接数，默认 10 */
    private int poolMaxTotal = 10;

    // ======================== 构造器 ========================

    /**
     * 无参构造 —— 使用所有默认值.
     */
    public TcpConfig() {
    }

    /**
     * 全参构造 —— 通过 Builder 调用.
     */
    public TcpConfig(String host, int port, int connectTimeoutMs, int readTimeoutMs,
                     boolean noDelay, boolean keepAlive, int poolMinIdle, int poolMaxTotal) {
        this.host = host;
        this.port = port;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.noDelay = noDelay;
        this.keepAlive = keepAlive;
        this.poolMinIdle = poolMinIdle;
        this.poolMaxTotal = poolMaxTotal;
    }

    // ======================== Builder ========================

    /**
     * 创建 Builder 实例.
     *
     * @return TcpConfig 构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * TcpConfig 的 Builder 模式构造器.
     */
    public static class Builder {
        private String host = "localhost";
        private int port = 8080;
        private int connectTimeoutMs = 10_000;
        private int readTimeoutMs = 60_000;
        private boolean noDelay = true;
        private boolean keepAlive = false;
        private int poolMinIdle = 2;
        private int poolMaxTotal = 10;

        /**
         * 设置远程主机.
         *
         * @param host 主机名或 IP
         * @return this
         */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /**
         * 设置远程端口.
         *
         * @param port 端口号
         * @return this
         */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /**
         * 设置连接超时（毫秒）.
         *
         * @param connectTimeoutMs 连接超时毫秒数
         * @return this
         */
        public Builder connectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
            return this;
        }

        /**
         * 设置读取超时（毫秒）.
         *
         * @param readTimeoutMs 读取超时毫秒数
         * @return this
         */
        public Builder readTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
            return this;
        }

        /**
         * 设置是否禁用 Nagle 算法.
         *
         * @param noDelay true = 禁用（小包即时发送）
         * @return this
         */
        public Builder noDelay(boolean noDelay) {
            this.noDelay = noDelay;
            return this;
        }

        /**
         * 设置是否启用 TCP KeepAlive.
         *
         * @param keepAlive true = 启用
         * @return this
         */
        public Builder keepAlive(boolean keepAlive) {
            this.keepAlive = keepAlive;
            return this;
        }

        /**
         * 设置连接池最小空闲连接数.
         *
         * @param poolMinIdle 最小空闲数
         * @return this
         */
        public Builder poolMinIdle(int poolMinIdle) {
            this.poolMinIdle = poolMinIdle;
            return this;
        }

        /**
         * 设置连接池最大连接数.
         *
         * @param poolMaxTotal 最大总数
         * @return this
         */
        public Builder poolMaxTotal(int poolMaxTotal) {
            this.poolMaxTotal = poolMaxTotal;
            return this;
        }

        /**
         * 构建 TcpConfig 实例.
         *
         * @return 配置实例
         */
        public TcpConfig build() {
            return new TcpConfig(host, port, connectTimeoutMs, readTimeoutMs,
                    noDelay, keepAlive, poolMinIdle, poolMaxTotal);
        }
    }

    // ======================== Getter / Setter ========================

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public boolean isNoDelay() {
        return noDelay;
    }

    public void setNoDelay(boolean noDelay) {
        this.noDelay = noDelay;
    }

    public boolean isKeepAlive() {
        return keepAlive;
    }

    public void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    public int getPoolMinIdle() {
        return poolMinIdle;
    }

    public void setPoolMinIdle(int poolMinIdle) {
        this.poolMinIdle = poolMinIdle;
    }

    public int getPoolMaxTotal() {
        return poolMaxTotal;
    }

    public void setPoolMaxTotal(int poolMaxTotal) {
        this.poolMaxTotal = poolMaxTotal;
    }
}

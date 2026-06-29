package com.github.leyland.letool.net.gateway;

import com.github.leyland.letool.net.protocol.ProtocolCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关路由定义 —— 描述一条完整的端到端路由配置，包含后端服务器、协议、连接模式、健康检查、故障转移和熔断策略.
 *
 * <h3>两种类型</h3>
 * <ul>
 *   <li><b>TCP 路由</b> —— 面向自定义协议或 JSON over TCP 的后端服务集群</li>
 *   <li><b>HTTP 路由</b> —— 面向 RESTful API 的后端微服务集群</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * GatewayRoute route = GatewayRoute.tcp("icbc-pay", lengthFieldCodec)
 *         .server("192.168.1.10", 8088, 1)
 *         .server("192.168.1.11", 8088, 2)
 *         .healthCheck("tcp-connect", null, 30)
 *         .failover("retry-next", 3)
 *         .circuitBreak(0.5, 60, 30)
 *         .build();
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class GatewayRoute {

    // ======================== 枚举：路由类型 ========================

    /**
     * 路由类型枚举.
     */
    public enum RouteType {
        /** TCP 路由 */
        TCP,
        /** HTTP 路由 */
        HTTP
    }

    /**
     * 连接模式枚举.
     */
    public enum ConnectionMode {
        /** 短连接 —— 每次请求建立新连接 */
        SHORT,
        /** 长连接 —— 维护持久连接池 */
        LONG
    }

    // ======================== 字段 ========================

    /** 路由唯一标识 */
    private String routeId;

    /** 路由类型（TCP / HTTP） */
    private RouteType type;

    /** 后端服务器列表 */
    private List<BackendServer> servers = new ArrayList<>();

    /** 协议编解码器（TCP 路由需要） */
    private ProtocolCodec protocol;

    /** 连接模式（短连接 / 长连接） */
    private ConnectionMode connectionMode = ConnectionMode.SHORT;

    /** 健康检查配置 */
    private HealthCheckConfig healthCheck;

    /** 故障转移配置 */
    private FailoverConfig failover;

    /** 熔断器配置 */
    private CircuitBreakConfig circuitBreak;

    // ======================== 构造器 ========================

    /**
     * 默认构造.
     */
    public GatewayRoute() {
    }

    // ======================== 静态工厂方法 ========================

    /**
     * 创建 TCP 路由 Builder.
     *
     * @param routeId  路由标识
     * @param protocol 协议编解码器
     * @return GatewayRoute 构建器
     */
    public static Builder tcp(String routeId, ProtocolCodec protocol) {
        return new Builder().routeId(routeId).type(RouteType.TCP).protocol(protocol);
    }

    /**
     * 创建 HTTP 路由 Builder.
     *
     * @param routeId 路由标识
     * @return GatewayRoute 构建器
     */
    public static Builder http(String routeId) {
        return new Builder().routeId(routeId).type(RouteType.HTTP);
    }

    // ======================== Builder ========================

    /**
     * GatewayRoute Builder 模式构造器.
     */
    public static class Builder {
        private String routeId;
        private RouteType type = RouteType.TCP;
        private final List<BackendServer> servers = new ArrayList<>();
        private ProtocolCodec protocol;
        private ConnectionMode connectionMode = ConnectionMode.SHORT;
        private HealthCheckConfig healthCheck;
        private FailoverConfig failover;
        private CircuitBreakConfig circuitBreak;

        public Builder routeId(String routeId) { this.routeId = routeId; return this; }
        public Builder type(RouteType type) { this.type = type; return this; }
        public Builder protocol(ProtocolCodec protocol) { this.protocol = protocol; return this; }
        public Builder connectionMode(ConnectionMode mode) { this.connectionMode = mode; return this; }

        /**
         * 添加后端服务器.
         *
         * @param host   主机名
         * @param port   端口
         * @param weight 权重
         * @return this
         */
        public Builder server(String host, int port, int weight) {
            this.servers.add(new BackendServer(host, port, weight, true,
                    type == RouteType.HTTP ? "http" : "tcp"));
            return this;
        }

        /**
         * 添加后端服务器对象.
         *
         * @param server 后端服务器
         * @return this
         */
        public Builder server(BackendServer server) {
            this.servers.add(server);
            return this;
        }

        /**
         * 设置健康检查配置.
         *
         * @param type          检查类型（tcp-connect / http）
         * @param path          HTTP 健康检查路径（仅 type=http 时有效）
         * @param intervalSec  检查间隔（秒）
         * @return this
         */
        public Builder healthCheck(String type, String path, int intervalSec) {
            this.healthCheck = new HealthCheckConfig(type, path, intervalSec);
            return this;
        }

        /**
         * 设置故障转移配置.
         *
         * @param strategy    策略（retry-next / retry-same / fail-fast）
         * @param maxRetries  最大重试次数
         * @return this
         */
        public Builder failover(String strategy, int maxRetries) {
            this.failover = new FailoverConfig(strategy, maxRetries);
            return this;
        }

        /**
         * 设置熔断配置.
         *
         * @param errorThreshold  失败率阈值（0.0 ~ 1.0）
         * @param windowSeconds   统计窗口秒数
         * @param recoverySeconds 恢复等待秒数
         * @return this
         */
        public Builder circuitBreak(double errorThreshold, int windowSeconds, int recoverySeconds) {
            this.circuitBreak = new CircuitBreakConfig(errorThreshold, windowSeconds, recoverySeconds);
            return this;
        }

        /**
         * 构建 GatewayRoute 实例.
         *
         * @return 路由实例
         */
        public GatewayRoute build() {
            GatewayRoute route = new GatewayRoute();
            route.routeId = this.routeId;
            route.type = this.type;
            route.servers = new ArrayList<>(this.servers);
            route.protocol = this.protocol;
            route.connectionMode = this.connectionMode;
            route.healthCheck = this.healthCheck;
            route.failover = this.failover;
            route.circuitBreak = this.circuitBreak;
            return route;
        }
    }

    // ======================== 内部配置类：HealthCheckConfig ========================

    /**
     * 健康检查配置模型.
     */
    public static class HealthCheckConfig {
        /** 健康检查类型：tcp-connect 或 http */
        private String type = "tcp-connect";

        /** HTTP 健康检查路径 */
        private String path = "/health";

        /** 健康检查间隔（秒） */
        private int intervalSec = 30;

        public HealthCheckConfig() {}

        public HealthCheckConfig(String type, String path, int intervalSec) {
            this.type = type != null ? type : "tcp-connect";
            this.path = path != null ? path : "/health";
            this.intervalSec = intervalSec > 0 ? intervalSec : 30;
        }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public int getIntervalSec() { return intervalSec; }
        public void setIntervalSec(int intervalSec) { this.intervalSec = intervalSec; }
    }

    // ======================== 内部配置类：FailoverConfig ========================

    /**
     * 故障转移配置模型.
     */
    public static class FailoverConfig {
        /** 故障转移策略 */
        private String strategy = "retry-next";

        /** 最大重试次数 */
        private int maxRetries = 3;

        public FailoverConfig() {}

        public FailoverConfig(String strategy, int maxRetries) {
            this.strategy = strategy != null ? strategy : "retry-next";
            this.maxRetries = maxRetries > 0 ? maxRetries : 3;
        }

        public String getStrategy() { return strategy; }
        public void setStrategy(String strategy) { this.strategy = strategy; }
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    }

    // ======================== 内部配置类：CircuitBreakConfig ========================

    /**
     * 熔断器配置模型.
     */
    public static class CircuitBreakConfig {
        /** 错误率阈值（0.0 ~ 1.0） */
        private double errorThreshold = 0.5;

        /** 统计窗口秒数 */
        private int windowSeconds = 60;

        /** 恢复等待秒数 */
        private int recoverySeconds = 30;

        public CircuitBreakConfig() {}

        public CircuitBreakConfig(double errorThreshold, int windowSeconds, int recoverySeconds) {
            this.errorThreshold = Math.max(0.0, Math.min(1.0, errorThreshold));
            this.windowSeconds = Math.max(1, windowSeconds);
            this.recoverySeconds = Math.max(1, recoverySeconds);
        }

        public double getErrorThreshold() { return errorThreshold; }
        public void setErrorThreshold(double errorThreshold) { this.errorThreshold = errorThreshold; }
        public int getWindowSeconds() { return windowSeconds; }
        public void setWindowSeconds(int windowSeconds) { this.windowSeconds = windowSeconds; }
        public int getRecoverySeconds() { return recoverySeconds; }
        public void setRecoverySeconds(int recoverySeconds) { this.recoverySeconds = recoverySeconds; }
    }

    // ======================== Getter / Setter ========================

    public String getRouteId() { return routeId; }
    public void setRouteId(String routeId) { this.routeId = routeId; }
    public RouteType getType() { return type; }
    public void setType(RouteType type) { this.type = type; }
    public List<BackendServer> getServers() { return servers; }
    public void setServers(List<BackendServer> servers) { this.servers = servers; }
    public ProtocolCodec getProtocol() { return protocol; }
    public void setProtocol(ProtocolCodec protocol) { this.protocol = protocol; }
    public ConnectionMode getConnectionMode() { return connectionMode; }
    public void setConnectionMode(ConnectionMode connectionMode) { this.connectionMode = connectionMode; }
    public HealthCheckConfig getHealthCheck() { return healthCheck; }
    public void setHealthCheck(HealthCheckConfig healthCheck) { this.healthCheck = healthCheck; }
    public FailoverConfig getFailover() { return failover; }
    public void setFailover(FailoverConfig failover) { this.failover = failover; }
    public CircuitBreakConfig getCircuitBreak() { return circuitBreak; }
    public void setCircuitBreak(CircuitBreakConfig circuitBreak) { this.circuitBreak = circuitBreak; }

    @Override
    public String toString() {
        return "GatewayRoute{id=" + routeId + ", type=" + type + ", servers=" + servers.size() + "}";
    }
}

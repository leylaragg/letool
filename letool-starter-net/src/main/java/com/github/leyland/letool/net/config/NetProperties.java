package com.github.leyland.letool.net.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 网络通信框架统一配置属性 —— 绑定 {@code letool.net} 前缀的所有配置项.
 *
 * <h3>配置分区</h3>
 * <ul>
 *   <li>{@code letool.net.http.*} —— HTTP 客户端参数（连接池、超时等）</li>
 *   <li>{@code letool.net.tcp.*} —— TCP 连接参数（超时、Socket 选项）</li>
 *   <li>{@code letool.net.gateway.*} —— 网关路由配置（多路由、健康检查、熔断、故障转移）</li>
 * </ul>
 *
 * <p>典型 YAML 示例：</p>
 * <pre>{@code
 * letool:
 *   net:
 *     http:
 *       enabled: true
 *       connect-timeout: 5s
 *       read-timeout: 30s
 *     tcp:
 *       enabled: true
 *       default-connect-timeout: 10s
 *       no-delay: true
 *     gateway:
 *       enabled: true
 *       routes:
 *         - route-id: "icbc-pay"
 *           type: tcp
 *           protocol: json
 *           servers:
 *             - host: "192.168.1.10"
 *               port: 8088
 *               weight: 1
 *           health-check:
 *             type: tcp-connect
 *             interval: 30s
 *           circuit-break:
 *             error-threshold: 0.5
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
@ConfigurationProperties(prefix = "letool.net")
public class NetProperties {

    // ======================== HTTP 配置 ========================

    /**
     * HTTP 客户端配置.
     */
    private Http http = new Http();

    // ======================== TCP 配置 ========================

    /**
     * TCP 客户端配置.
     */
    private Tcp tcp = new Tcp();

    // ======================== 网关配置 ========================

    /**
     * 网关路由配置.
     */
    private Gateway gateway = new Gateway();

    // ======================== Getter / Setter ========================

    public Http getHttp() {
        return http;
    }

    public void setHttp(Http http) {
        this.http = http;
    }

    public Tcp getTcp() {
        return tcp;
    }

    public void setTcp(Tcp tcp) {
        this.tcp = tcp;
    }

    public Gateway getGateway() {
        return gateway;
    }

    public void setGateway(Gateway gateway) {
        this.gateway = gateway;
    }

    // ======================== 内部类：HTTP ========================

    /**
     * HTTP 客户端配置模型.
     */
    public static class Http {

        /**
         * 是否启用 HTTP 客户端，默认关闭.
         */
        private boolean enabled = false;

        /**
         * 连接超时时间，默认 5 秒.
         */
        private String connectTimeout = "5s";

        /**
         * 读取超时时间，默认 30 秒.
         */
        private String readTimeout = "30s";

        /**
         * 连接池最大连接数，默认 200.
         */
        private int maxTotal = 200;

        /**
         * 单路由最大连接数，默认 50.
         */
        private int maxPerRoute = 50;

        // ---- Getter / Setter ----

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(String connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public String getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(String readTimeout) {
            this.readTimeout = readTimeout;
        }

        public int getMaxTotal() {
            return maxTotal;
        }

        public void setMaxTotal(int maxTotal) {
            this.maxTotal = maxTotal;
        }

        public int getMaxPerRoute() {
            return maxPerRoute;
        }

        public void setMaxPerRoute(int maxPerRoute) {
            this.maxPerRoute = maxPerRoute;
        }
    }

    // ======================== 内部类：TCP ========================

    /**
     * TCP 客户端配置模型.
     */
    public static class Tcp {

        /**
         * 是否启用 TCP 客户端，默认关闭.
         */
        private boolean enabled = false;

        /**
         * 默认连接超时时间，默认 10 秒.
         */
        private String defaultConnectTimeout = "10s";

        /**
         * 默认读取超时时间，默认 60 秒.
         */
        private String defaultReadTimeout = "60s";

        /**
         * 是否禁用 Nagle 算法（true = TCP_NODELAY），默认 true.
         */
        private boolean noDelay = true;

        /**
         * 是否启用 KeepAlive（TCP_KEEPALIVE），默认 false.
         */
        private boolean keepAlive = false;

        // ---- Getter / Setter ----

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getDefaultConnectTimeout() {
            return defaultConnectTimeout;
        }

        public void setDefaultConnectTimeout(String defaultConnectTimeout) {
            this.defaultConnectTimeout = defaultConnectTimeout;
        }

        public String getDefaultReadTimeout() {
            return defaultReadTimeout;
        }

        public void setDefaultReadTimeout(String defaultReadTimeout) {
            this.defaultReadTimeout = defaultReadTimeout;
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
    }

    // ======================== 内部类：Gateway ========================

    /**
     * 网关整体配置模型 —— 包含多条路由定义.
     */
    public static class Gateway {

        /**
         * 是否启用网关功能，默认关闭.
         */
        private boolean enabled = false;

        /**
         * 路由列表.
         */
        private List<Route> routes = new ArrayList<>();

        // ---- Getter / Setter ----

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<Route> getRoutes() {
            return routes;
        }

        public void setRoutes(List<Route> routes) {
            this.routes = routes;
        }

        // ======================== 内部类：Route ========================

        /**
         * 单一路由配置模型.
         * <p>
         * 每个 Route 对应一个后端服务集群（可以是一个主备或多实例），
         * 包含服务器列表、协议、连接模式、健康检查、故障转移和熔断策略.
         * </p>
         */
        public static class Route {

            /**
             * 路由唯一标识.
             */
            private String routeId;

            /**
             * 路由类型：{@code tcp} 或 {@code http}.
             */
            private String type = "http";

            /**
             * 协议名称，如 {@code json}、{@code fixed-length}、{@code length-field} 等.
             */
            private String protocol;

            /**
             * 连接模式：{@code short} 短连接，{@code long} 长连接.
             */
            private String connectionMode = "short";

            /**
             * 后端服务器列表.
             */
            private List<Server> servers = new ArrayList<>();

            /**
             * 健康检查配置.
             */
            private HealthCheck healthCheck = new HealthCheck();

            /**
             * 故障转移策略配置.
             */
            private Failover failover = new Failover();

            /**
             * 熔断器配置.
             */
            private CircuitBreak circuitBreak = new CircuitBreak();

            // ---- Getter / Setter ----

            public String getRouteId() {
                return routeId;
            }

            public void setRouteId(String routeId) {
                this.routeId = routeId;
            }

            public String getType() {
                return type;
            }

            public void setType(String type) {
                this.type = type;
            }

            public String getProtocol() {
                return protocol;
            }

            public void setProtocol(String protocol) {
                this.protocol = protocol;
            }

            public String getConnectionMode() {
                return connectionMode;
            }

            public void setConnectionMode(String connectionMode) {
                this.connectionMode = connectionMode;
            }

            public List<Server> getServers() {
                return servers;
            }

            public void setServers(List<Server> servers) {
                this.servers = servers;
            }

            public HealthCheck getHealthCheck() {
                return healthCheck;
            }

            public void setHealthCheck(HealthCheck healthCheck) {
                this.healthCheck = healthCheck;
            }

            public Failover getFailover() {
                return failover;
            }

            public void setFailover(Failover failover) {
                this.failover = failover;
            }

            public CircuitBreak getCircuitBreak() {
                return circuitBreak;
            }

            public void setCircuitBreak(CircuitBreak circuitBreak) {
                this.circuitBreak = circuitBreak;
            }

            // ======================== 内部类：Server ========================

            /**
             * 后端服务器配置模型.
             */
            public static class Server {

                /**
                 * 服务器主机名或 IP 地址.
                 */
                private String host = "localhost";

                /**
                 * 服务器端口.
                 */
                private int port = 80;

                /**
                 * 权重（用于加权负载均衡），默认 1.
                 */
                private int weight = 1;

                // ---- Getter / Setter ----

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

                public int getWeight() {
                    return weight;
                }

                public void setWeight(int weight) {
                    this.weight = weight;
                }
            }

            // ======================== 内部类：HealthCheck ========================

            /**
             * 健康检查配置模型.
             */
            public static class HealthCheck {

                /**
                 * 健康检查类型：{@code tcp-connect} 或 {@code http}.
                 */
                private String type = "tcp-connect";

                /**
                 * HTTP 健康检查路径（仅 type=http 时有效）.
                 */
                private String path = "/health";

                /**
                 * 健康检查间隔，默认 30 秒.
                 */
                private String interval = "30s";

                // ---- Getter / Setter ----

                public String getType() {
                    return type;
                }

                public void setType(String type) {
                    this.type = type;
                }

                public String getPath() {
                    return path;
                }

                public void setPath(String path) {
                    this.path = path;
                }

                public String getInterval() {
                    return interval;
                }

                public void setInterval(String interval) {
                    this.interval = interval;
                }
            }

            // ======================== 内部类：Failover ========================

            /**
             * 故障转移配置模型.
             */
            public static class Failover {

                /**
                 * 故障转移策略：{@code retry-next}（重试下一节点）、
                 * {@code retry-same}（重试本节点）、{@code fail-fast}（快速失败）.
                 */
                private String strategy = "retry-next";

                /**
                 * 最大重试次数，默认 3.
                 */
                private int maxRetries = 3;

                // ---- Getter / Setter ----

                public String getStrategy() {
                    return strategy;
                }

                public void setStrategy(String strategy) {
                    this.strategy = strategy;
                }

                public int getMaxRetries() {
                    return maxRetries;
                }

                public void setMaxRetries(int maxRetries) {
                    this.maxRetries = maxRetries;
                }
            }

            // ======================== 内部类：CircuitBreak ========================

            /**
             * 熔断器配置模型.
             */
            public static class CircuitBreak {

                /**
                 * 错误率阈值（0.0 ~ 1.0），超过即熔断，默认 0.5.
                 */
                private double errorThreshold = 0.5;

                /**
                 * 统计窗口秒数，默认 60 秒.
                 */
                private int windowSeconds = 60;

                /**
                 * 熔断恢复等待秒数，默认 30 秒.
                 */
                private int recoverySeconds = 30;

                // ---- Getter / Setter ----

                public double getErrorThreshold() {
                    return errorThreshold;
                }

                public void setErrorThreshold(double errorThreshold) {
                    this.errorThreshold = errorThreshold;
                }

                public int getWindowSeconds() {
                    return windowSeconds;
                }

                public void setWindowSeconds(int windowSeconds) {
                    this.windowSeconds = windowSeconds;
                }

                public int getRecoverySeconds() {
                    return recoverySeconds;
                }

                public void setRecoverySeconds(int recoverySeconds) {
                    this.recoverySeconds = recoverySeconds;
                }
            }
        }
    }
}

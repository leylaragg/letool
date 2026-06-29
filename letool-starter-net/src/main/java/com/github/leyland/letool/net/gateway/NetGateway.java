package com.github.leyland.letool.net.gateway;

import com.github.leyland.letool.net.config.NetProperties;
import com.github.leyland.letool.net.exception.NetException;
import com.github.leyland.letool.net.http.NetHttpTemplate;
import com.github.leyland.letool.net.protocol.ProtocolCodec;
import com.github.leyland.letool.net.tcp.TcpClient;
import com.github.leyland.letool.net.tcp.TcpConfig;
import com.github.leyland.letool.net.tcp.TcpLongClient;
import com.github.leyland.letool.net.tcp.TcpShortClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 企业级统一网络网关 —— 所有网络通信的中央入口，统一管理 TCP 和 HTTP 路由.
 *
 * <p>NetGateway 是整个 {@code letool-starter-net} 模块的核心入口，对外提供一致的 API 来
 * 处理 TCP 和 HTTP 两种协议的通信。内部维护一个路由表，每条路由绑定到一组后端服务器，
 * 并自动创建对应的 TCP 客户端或 HTTP 模板.</p>
 *
 * <h3>路由配置示例（编程式）</h3>
 * <pre>{@code
 * NetGateway gateway = NetGateway.builder()
 *         .route("icbc-pay", GatewayRoute.tcp("icbc-pay", lengthFieldCodec)
 *                 .server("192.168.1.10", 8088, 1)
 *                 .connectionMode(ConnectionMode.LONG)
 *                 .healthCheck("tcp-connect", null, 30)
 *                 .circuitBreak(0.5, 60, 30)
 *                 .build())
 *         .route("user-service", GatewayRoute.http("user-service")
 *                 .server("localhost", 8080, 1)
 *                 .server("localhost", 8081, 2)
 *                 .build())
 *         .build();
 * }</pre>
 *
 * <h3>路由配置示例（YAML 声明式）</h3>
 * <pre>{@code
 * letool:
 *   net:
 *     gateway:
 *       enabled: true
 *       routes:
 *         - route-id: icbc-pay
 *           type: tcp
 *           protocol: length-field
 *           connection-mode: long
 *           servers:
 *             - host: 192.168.1.10
 *               port: 8088
 *               weight: 1
 *           health-check:
 *             type: tcp-connect
 *             interval: 30s
 *           circuit-break:
 *             error-threshold: 0.5
 *         - route-id: user-service
 *           type: http
 *           servers:
 *             - host: localhost
 *               port: 8080
 *               weight: 1
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class NetGateway {

    private static final Logger log = LoggerFactory.getLogger(NetGateway.class);

    // ======================== 字段 ========================

    /** 网络配置属性 */
    private final NetProperties properties;

    /** 路由器（管理路由注册和查找） */
    private final GatewayRouter router;

    /** 路由 ID -> TcpClient 映射（TCP 路由） */
    private final Map<String, TcpClient> tcpClients = new ConcurrentHashMap<>();

    /** 路由 ID -> NetHttpTemplate 映射（HTTP 路由） */
    private final Map<String, NetHttpTemplate> httpTemplates = new ConcurrentHashMap<>();

    // ======================== 构造器 ========================

    /**
     * 从配置属性构建 NetGateway.
     *
     * @param properties 网络配置属性
     */
    public NetGateway(NetProperties properties) {
        this.properties = properties;
        this.router = new GatewayRouter();
        log.info("NetGateway initialized");
    }

    /**
     * 无参构造（使用默认配置）.
     */
    public NetGateway() {
        this(new NetProperties());
    }

    // ======================== Builder ========================

    /**
     * 创建 NetGateway Builder.
     *
     * @return Builder 实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * NetGateway 的 Builder 模式构造器.
     */
    public static class Builder {
        private final NetGateway gateway = new NetGateway();

        /**
         * 添加一条路由.
         *
         * @param route 路由定义
         * @return this
         */
        public Builder route(GatewayRoute route) {
            gateway.registerRoute(route);
            return this;
        }

        /**
         * 添加一条路由并自动 Apply（等价于 route + 按 routeId 设置第二个参数）.
         *
         * @param routeId 路由 ID
         * @param route   路由定义
         * @return this
         */
        public Builder route(String routeId, GatewayRoute route) {
            gateway.registerRoute(route);
            return this;
        }

        /**
         * 构建 NetGateway 实例.
         *
         * @return NetGateway 实例
         */
        public NetGateway build() {
            return gateway;
        }
    }

    // ======================== 路由管理 ========================

    /**
     * 注册一条网关路由.
     *
     * @param route 路由定义
     * @throws NetException 如果 routeId 已存在
     */
    public void registerRoute(GatewayRoute route) {
        router.registerRoute(route);
        log.info("Registered route: {} (type={})", route.getRouteId(), route.getType());
    }

    /**
     * 注销一条路由.
     *
     * @param routeId 路由标识
     * @return {@code true} 如果找到并移除
     */
    public boolean removeRoute(String routeId) {
        // 清理关联的客户端
        TcpClient tcp = tcpClients.remove(routeId);
        if (tcp != null) {
            tcp.disconnect();
        }
        httpTemplates.remove(routeId);

        return router.removeRoute(routeId);
    }

    /**
     * 按 routeId 查找路由.
     *
     * @param routeId 路由标识
     * @return 路由定义，未找到返回 {@code null}
     */
    public GatewayRoute getRoute(String routeId) {
        return router.route(routeId);
    }

    // ======================== HTTP 访问 ========================

    /**
     * 获取指定路由 ID 的 HTTP 模板.
     *
     * @param routeId 路由标识
     * @return HTTP 模板实例
     * @throws NetException 如果路由不存在或类型不是 HTTP
     */
    public NetHttpTemplate http(String routeId) {
        GatewayRoute route = router.route(routeId);
        if (route == null) {
            throw new NetException("Route not found: " + routeId, routeId);
        }
        if (route.getType() != GatewayRoute.RouteType.HTTP) {
            throw new NetException("Route is not HTTP type: " + routeId, routeId);
        }
        return httpTemplates.computeIfAbsent(routeId, id -> {
            NetHttpTemplate template = new NetHttpTemplate(properties);
            log.info("Created HTTP template for route: {}", routeId);
            return template;
        });
    }

    // ======================== TCP 访问 ========================

    /**
     * 获取指定路由 ID 的 TCP 客户端.
     *
     * @param routeId 路由标识
     * @return TCP 客户端实例
     * @throws NetException 如果路由不存在或类型不是 TCP
     */
    public TcpClient tcp(String routeId) {
        GatewayRoute route = router.route(routeId);
        if (route == null) {
            throw new NetException("Route not found: " + routeId, routeId);
        }
        if (route.getType() != GatewayRoute.RouteType.TCP) {
            throw new NetException("Route is not TCP type: " + routeId, routeId);
        }
        return tcpClients.computeIfAbsent(routeId, id -> createTcpClient(route));
    }

    /**
     * 根据路由定义创建 TCP 客户端.
     *
     * @param route 路由定义
     * @return TCP 客户端
     */
    private TcpClient createTcpClient(GatewayRoute route) {
        if (route.getServers().isEmpty()) {
            throw new NetException("No servers defined for route: " + route.getRouteId(), route.getRouteId());
        }
        // 取第一个服务器（后续可扩展为多服务器负载均衡）
        BackendServer server = route.getServers().get(0);
        ProtocolCodec codec = route.getProtocol();

        boolean isLongConnection = route.getConnectionMode() == GatewayRoute.ConnectionMode.LONG;

        TcpClient client;
        if (isLongConnection) {
            client = new TcpLongClient(server.getHost(), server.getPort(), codec, 2, 10);
            client.connect(); // 长连接在创建时就建立连接
        } else {
            client = new TcpShortClient(server.getHost(), server.getPort(), codec, 10000, 60000);
        }

        log.info("Created TCP client for route '{}': {} ({}), host={}:{}",
                route.getRouteId(),
                isLongConnection ? "LONG" : "SHORT",
                codec != null ? codec.getProtocolName() : "no-codec",
                server.getHost(), server.getPort());
        return client;
    }

    // ======================== send (统一发送) ========================

    /**
     * 通过指定路由发送消息，自动根据路由类型选择 TCP 或 HTTP 通道.
     *
     * <p>TCP 路由：消息会通过协议编解码器编码后发送，返回原始字节.</p>
     * <p>HTTP 路由：消息的 toString() 将作为 HTTP POST body 发送，返回响应字符串.</p>
     *
     * @param routeId 路由标识
     * @param message 待发送的消息对象
     * @return 响应（TCP 返回 byte[]，HTTP 返回 String）
     * @throws NetException 如果路由不存在或通信失败
     */
    public Object send(String routeId, Object message) {
        GatewayRoute route = router.route(routeId);
        if (route == null) {
            throw new NetException("Route not found: " + routeId, routeId);
        }

        if (route.getType() == GatewayRoute.RouteType.TCP) {
            TcpClient client = tcp(routeId);
            ProtocolCodec codec = route.getProtocol();
            if (codec == null) {
                throw new NetException("No protocol codec for TCP route: " + routeId, routeId);
            }
            byte[] encoded = codec.encode(message);
            return client.sendAndReceive(encoded);
        } else {
            NetHttpTemplate template = http(routeId);
            // 对于 HTTP，默认取第一个服务器的 URL
            if (route.getServers().isEmpty()) {
                throw new NetException("No servers for HTTP route: " + routeId, routeId);
            }
            BackendServer server = route.getServers().get(0);
            String url = server.getScheme() + "://" + server.getHost() + ":" + server.getPort();
            return template.post(url, message);
        }
    }

    // ======================== 生命周期 ========================

    /**
     * 关闭网关，释放所有 TCP 连接和健康检查器.
     */
    public void close() {
        for (TcpClient client : tcpClients.values()) {
            try {
                client.disconnect();
            } catch (Exception e) {
                log.warn("Error disconnecting TCP client: {}", e.getMessage());
            }
        }
        tcpClients.clear();
        httpTemplates.clear();
        log.info("NetGateway closed");
    }
}

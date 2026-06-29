package com.github.leyland.letool.websocket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WebSocket 模块配置属性类，对应 YAML 中的 {@code letool.websocket} 前缀。
 *
 * <p>该配置类聚合了 WebSocket 连接路径、跨域、会话上限、帧大小、心跳检测、鉴权等所有配置项。
 * 使用者可在 {@code application.yml} 中按如下结构配置：</p>
 *
 * <pre>{@code
 * letool:
 *   websocket:
 *     enabled: true              # 是否启用 WebSocket 模块
 *     path: /ws                  # WebSocket 连接端点路径
 *     allowed-origins: "*"       # 允许的跨域来源
 *     max-session-per-user: 5    # 单用户最大会话数
 *     max-frame-size: 65536      # 最大帧大小（字节）
 *     heartbeat:
 *       enabled: true            # 是否启用心跳检测
 *       interval: 30s            # 心跳检测间隔
 *       timeout: 90s             # 心跳超时时间
 *     auth:
 *       enabled: true            # 是否启用连接鉴权
 *       token-param: token       # 鉴权 Token 的查询参数名
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
@ConfigurationProperties(prefix = "letool.websocket")
public class WebSocketProperties {

    // ======================== 顶层属性 ========================

    /** 是否启用 WebSocket 模块，默认 true */
    private boolean enabled = true;

    /** WebSocket 连接端点路径，默认 /ws */
    private String path = "/ws";

    /** 允许的跨域来源，多个用逗号分隔，默认 *（允许所有） */
    private String allowedOrigins = "*";

    /** 单个用户最大同时在线会话数，默认 5 */
    private int maxSessionPerUser = 5;

    /** WebSocket 最大帧大小（字节），默认 65536（64KB） */
    private int maxFrameSize = 65536;

    /** 心跳检测配置 */
    private Heartbeat heartbeat = new Heartbeat();

    /** 连接鉴权配置 */
    private Auth auth = new Auth();

    // ======================== Getter / Setter ========================

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getPath() { return path; }

    public void setPath(String path) { this.path = path; }

    public String getAllowedOrigins() { return allowedOrigins; }

    public void setAllowedOrigins(String allowedOrigins) { this.allowedOrigins = allowedOrigins; }

    public int getMaxSessionPerUser() { return maxSessionPerUser; }

    public void setMaxSessionPerUser(int maxSessionPerUser) { this.maxSessionPerUser = maxSessionPerUser; }

    public int getMaxFrameSize() { return maxFrameSize; }

    public void setMaxFrameSize(int maxFrameSize) { this.maxFrameSize = maxFrameSize; }

    public Heartbeat getHeartbeat() { return heartbeat; }

    public void setHeartbeat(Heartbeat heartbeat) { this.heartbeat = heartbeat; }

    public Auth getAuth() { return auth; }

    public void setAuth(Auth auth) { this.auth = auth; }

    // ======================== 内嵌类：心跳检测配置 ========================

    /**
     * WebSocket 心跳检测配置。
     *
     * <p>服务端定期检查客户端心跳，超时未收到心跳的会话将被自动断开，
     * 用于及时清理网络断开但未发送 close 帧的僵尸连接。</p>
     */
    public static class Heartbeat {

        /** 是否启用心跳检测，默认 true */
        private boolean enabled = true;

        /** 心跳检测间隔时间（秒），默认 30 */
        private int interval = 30;

        /** 心跳超时时间（秒），超过此时间未收到心跳视为超时，默认 90 */
        private int timeout = 90;

        // ---- Getter / Setter ----

        public boolean isEnabled() { return enabled; }

        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getInterval() { return interval; }

        public void setInterval(int interval) { this.interval = interval; }

        public int getTimeout() { return timeout; }

        public void setTimeout(int timeout) { this.timeout = timeout; }
    }

    // ======================== 内嵌类：鉴权配置 ========================

    /**
     * WebSocket 连接鉴权配置。
     *
     * <p>通过 URL 查询参数传递 Token 进行鉴权，鉴权通过后建立 WsPrincipal 并绑定到会话属性中。
     * 若 {@code enabled} 为 {@code false}，则允许匿名连接。</p>
     */
    public static class Auth {

        /** 是否启用连接鉴权，默认 true */
        private boolean enabled = true;

        /** 鉴权 Token 的 URL 查询参数名，默认 token */
        private String tokenParam = "token";

        // ---- Getter / Setter ----

        public boolean isEnabled() { return enabled; }

        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getTokenParam() { return tokenParam; }

        public void setTokenParam(String tokenParam) { this.tokenParam = tokenParam; }
    }
}

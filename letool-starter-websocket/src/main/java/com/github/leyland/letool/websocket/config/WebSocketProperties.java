package com.github.leyland.letool.websocket.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * WebSocket 模块配置，对应 {@code letool.websocket} 前缀。
 *
 * <p>默认只接受同源握手。只有业务明确配置 {@code allowed-origins} 后，
 * 才会开放跨域来源；鉴权默认启用并委托给 {@code WsAuthenticator}。</p>
 */
@ConfigurationProperties(prefix = "letool.websocket")
public class WebSocketProperties implements InitializingBean {

    private boolean enabled = true;
    private String path = "/ws";
    private List<String> allowedOrigins = new ArrayList<>();
    private int maxSessionPerUser = 5;
    private DataSize maxFrameSize = DataSize.ofKilobytes(64);
    private Duration sendTimeLimit = Duration.ofSeconds(10);
    private DataSize sendBufferSize = DataSize.ofKilobytes(512);
    private Heartbeat heartbeat = new Heartbeat();
    private Auth auth = new Auth();

    /**
     * 获取模块启用状态。
     *
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置模块启用状态。
     *
     * @param enabled 是否启用模块
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取 WebSocket 端点路径。
     *
     * @return 端点路径
     */
    public String getPath() {
        return path;
    }

    /**
     * 设置 WebSocket 端点路径。
     *
     * @param path 以斜杠开头的端点路径
     */
    public void setPath(String path) {
        if (path == null || path.isBlank() || !path.startsWith("/") || path.contains("?")) {
            throw new IllegalArgumentException("WebSocket 端点路径必须以 / 开头且不能包含查询参数");
        }
        this.path = path;
    }

    /**
     * 获取允许跨域握手的来源。
     *
     * @return 不可变来源列表，空列表表示沿用 Spring 同源策略
     */
    public List<String> getAllowedOrigins() {
        return List.copyOf(allowedOrigins);
    }

    /**
     * 设置允许跨域握手的来源。
     *
     * @param allowedOrigins 来源列表，可包含通配符 {@code *}
     */
    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins == null
                ? new ArrayList<>()
                : allowedOrigins.stream()
                .filter(origin -> origin != null && !origin.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    /**
     * 获取单用户会话上限。
     *
     * @return 会话上限
     */
    public int getMaxSessionPerUser() {
        return maxSessionPerUser;
    }

    /**
     * 设置单用户会话上限。
     *
     * @param maxSessionPerUser 会话上限
     */
    public void setMaxSessionPerUser(int maxSessionPerUser) {
        if (maxSessionPerUser <= 0) {
            throw new IllegalArgumentException("单用户 WebSocket 会话上限必须大于 0");
        }
        this.maxSessionPerUser = maxSessionPerUser;
    }

    /**
     * 获取单个文本帧大小上限。
     *
     * @return 字节数
     */
    public DataSize getMaxFrameSize() {
        return maxFrameSize;
    }

    /**
     * 设置单个文本帧大小上限。
     *
     * @param maxFrameSize 数据大小
     */
    public void setMaxFrameSize(DataSize maxFrameSize) {
        if (maxFrameSize == null || maxFrameSize.toBytes() <= 0) {
            throw new IllegalArgumentException("WebSocket 最大消息大小必须大于 0 字节");
        }
        requireIntBytes(maxFrameSize, "WebSocket 最大消息大小不能超过 2147483647 字节");
        this.maxFrameSize = maxFrameSize;
    }

    /**
     * 获取单次发送时间上限。
     *
     * @return 发送时间上限
     */
    public Duration getSendTimeLimit() {
        return sendTimeLimit;
    }

    /**
     * 设置单次发送时间上限。
     *
     * @param sendTimeLimit 发送时间上限
     */
    public void setSendTimeLimit(Duration sendTimeLimit) {
        requirePositive(sendTimeLimit, "WebSocket 发送时间上限必须大于 0");
        if (sendTimeLimit.toMillis() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("WebSocket 发送时间上限不能超过 2147483647 毫秒");
        }
        this.sendTimeLimit = sendTimeLimit;
    }

    /**
     * 获取单连接发送缓冲区上限。
     *
     * @return 缓冲区字节数
     */
    public DataSize getSendBufferSize() {
        return sendBufferSize;
    }

    /**
     * 设置单连接发送缓冲区上限。
     *
     * @param sendBufferSize 缓冲区大小
     */
    public void setSendBufferSize(DataSize sendBufferSize) {
        if (sendBufferSize == null || sendBufferSize.toBytes() <= 0) {
            throw new IllegalArgumentException("WebSocket 发送缓冲区大小必须大于 0 字节");
        }
        requireIntBytes(sendBufferSize, "WebSocket 发送缓冲区大小不能超过 2147483647 字节");
        this.sendBufferSize = sendBufferSize;
    }

    /**
     * 获取心跳配置。
     *
     * @return 心跳配置
     */
    public Heartbeat getHeartbeat() {
        return heartbeat;
    }

    /**
     * 设置心跳配置。
     *
     * @param heartbeat 心跳配置
     */
    public void setHeartbeat(Heartbeat heartbeat) {
        this.heartbeat = requireNonNull(heartbeat, "WebSocket 心跳配置不能为空");
    }

    /**
     * 获取鉴权配置。
     *
     * @return 鉴权配置
     */
    public Auth getAuth() {
        return auth;
    }

    /**
     * 设置鉴权配置。
     *
     * @param auth 鉴权配置
     */
    public void setAuth(Auth auth) {
        this.auth = requireNonNull(auth, "WebSocket 鉴权配置不能为空");
    }

    /**
     * 在属性绑定完成后校验跨字段约束。
     */
    @Override
    public void afterPropertiesSet() {
        if (heartbeat.enabled && heartbeat.timeout.compareTo(heartbeat.interval) <= 0) {
            throw new IllegalArgumentException("WebSocket 心跳超时时间必须大于检查间隔");
        }
    }

    /**
     * 校验时长必须为正数。
     *
     * @param duration 待校验时长
     * @param message 非法时的错误消息
     */
    private static void requirePositive(Duration duration, String message) {
        if (duration == null || duration.toMillis() <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 校验数据大小可以安全传入 Spring WebSocket 的整数参数。
     *
     * @param dataSize 待校验数据大小
     * @param message 非法时的错误消息
     */
    private static void requireIntBytes(DataSize dataSize, String message) {
        if (dataSize.toBytes() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 校验对象不能为空。
     *
     * @param value 待校验对象
     * @param message 非法时的错误消息
     * @param <T> 对象类型
     * @return 原对象
     */
    private static <T> T requireNonNull(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /**
     * WebSocket 心跳检测配置。
     */
    public static class Heartbeat {

        private boolean enabled = true;
        private Duration interval = Duration.ofSeconds(30);
        private Duration timeout = Duration.ofSeconds(90);

        /**
         * 获取心跳检测启用状态。
         *
         * @return 启用时返回 {@code true}
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置心跳检测启用状态。
         *
         * @param enabled 是否启用心跳检测
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 获取心跳检查间隔。
         *
         * @return 检查间隔
         */
        public Duration getInterval() {
            return interval;
        }

        /**
         * 设置心跳检查间隔。
         *
         * @param interval 检查间隔
         */
        public void setInterval(Duration interval) {
            requirePositive(interval, "WebSocket 心跳检查间隔必须大于 0");
            this.interval = interval;
        }

        /**
         * 获取心跳超时时间。
         *
         * @return 心跳超时时间
         */
        public Duration getTimeout() {
            return timeout;
        }

        /**
         * 设置心跳超时时间。
         *
         * @param timeout 心跳超时时间
         */
        public void setTimeout(Duration timeout) {
            requirePositive(timeout, "WebSocket 心跳超时时间必须大于 0");
            this.timeout = timeout;
        }
    }

    /**
     * WebSocket 握手鉴权配置。
     */
    public static class Auth {

        private boolean enabled = true;

        /**
         * 获取鉴权启用状态。
         *
         * @return 启用时返回 {@code true}
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置鉴权启用状态。
         *
         * @param enabled 是否启用鉴权
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}

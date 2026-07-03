package com.github.leyland.letool.websocket.config;

import com.github.leyland.letool.websocket.auth.WsHandshakeInterceptor;
import com.github.leyland.letool.websocket.core.WsSessionManager;
import com.github.leyland.letool.websocket.core.WsTemplate;
import com.github.leyland.letool.websocket.handler.DefaultWsHandler;
import com.github.leyland.letool.websocket.handler.WsMessageHandler;
import com.github.leyland.letool.websocket.heartbeat.HeartbeatDetector;
import com.github.leyland.letool.websocket.room.WsRoomManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Collections;
import java.util.List;

/**
 * WebSocket 模块自动配置类，负责创建并注册所有 WebSocket 核心组件。
 *
 * <p><b>Bean 创建策略：</b></p>
 * <ul>
 *   <li>{@code wsSessionManager} — 会话生命周期管理器（单例）</li>
 *   <li>{@code wsTemplate} — 消息发送模板，主要入口类</li>
 *   <li>{@code wsRoomManager} — 房间/频道管理器</li>
 *   <li>{@code heartbeatDetector} — 心跳检测器（可选，默认启用）</li>
 *   <li>{@code wsHandshakeInterceptor} — 握手鉴权拦截器</li>
 *   <li>{@code defaultWsHandler} — 默认 WebSocket 消息处理器</li>
 *   <li>{@code webSocketConfigurer} — 端点注册器（由 Spring WebSocket 自动发现并调用）</li>
 * </ul>
 *
 * <p>通过 {@code letool.websocket.enabled=false} 可禁用整个模块。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
@AutoConfiguration
@EnableWebSocket
@EnableConfigurationProperties(WebSocketProperties.class)
@ConditionalOnClass(WebSocketConfigurer.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "letool.websocket", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WebSocketAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAutoConfiguration.class);

    // ======================== 核心组件 Bean ========================

    /**
     * 创建 WebSocket 会话管理器 Bean。
     *
     * @return WsSessionManager 单例实例
     */
    @Bean
    @ConditionalOnMissingBean
    public WsSessionManager wsSessionManager() {
        log.info("Creating WsSessionManager");
        return new WsSessionManager();
    }

    /**
     * 创建 WebSocket 消息发送模板 Bean。
     *
     * @param sessionManager 会话管理器
     * @return WsTemplate 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public WsTemplate wsTemplate(WsSessionManager sessionManager) {
        log.info("Creating WsTemplate");
        return new WsTemplate(sessionManager);
    }

    /**
     * 创建 WebSocket 房间管理器 Bean。
     *
     * @param sessionManager 会话管理器
     * @return WsRoomManager 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public WsRoomManager wsRoomManager(WsSessionManager sessionManager) {
        log.info("Creating WsRoomManager");
        return new WsRoomManager(sessionManager);
    }

    /**
     * 创建心跳检测器 Bean。
     *
     * <p>如果 {@code letool.websocket.heartbeat.enabled} 为 {@code true}，
     * 则在创建后自动调用 {@link HeartbeatDetector#start()} 启动定时心跳检查。
     * 应用关闭时通过 {@code destroyMethod} 自动停止定时任务。</p>
     *
     * @param properties     WebSocket 配置属性
     * @param sessionManager 会话管理器
     * @return HeartbeatDetector 实例
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "letool.websocket.heartbeat", name = "enabled", havingValue = "true", matchIfMissing = true)
    public HeartbeatDetector heartbeatDetector(WebSocketProperties properties, WsSessionManager sessionManager) {
        log.info("Creating HeartbeatDetector");
        return new HeartbeatDetector(properties, sessionManager);
    }

    /**
     * 创建握手拦截器 Bean。
     *
     * @param properties WebSocket 配置属性
     * @return WsHandshakeInterceptor 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public WsHandshakeInterceptor wsHandshakeInterceptor(WebSocketProperties properties) {
        log.info("Creating WsHandshakeInterceptor");
        return new WsHandshakeInterceptor(properties);
    }

    /**
     * 创建默认 WebSocket 处理器 Bean。
     *
     * <p>查找所有实现了 {@link WsMessageHandler} 接口的 Bean，
     * 并自动注册到处理器中。如果未找到任何自定义处理器，则使用空列表。</p>
     *
     * @param sessionManager    会话管理器
     * @param roomManager       房间管理器
     * @param heartbeatDetector 心跳检测器（可能为 {@code null}，心跳功能禁用时不存在该 Bean）
     * @param handlers          消息处理器列表（可能为空）
     * @return DefaultWsHandler 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public DefaultWsHandler defaultWsHandler(WsSessionManager sessionManager,
                                              WsRoomManager roomManager,
                                              @Autowired(required = false) HeartbeatDetector heartbeatDetector,
                                              @Autowired(required = false) List<WsMessageHandler> handlers) {
        log.info("Creating DefaultWsHandler with {} message handlers",
                handlers != null ? handlers.size() : 0);
        return new DefaultWsHandler(sessionManager, roomManager, heartbeatDetector,
                handlers != null ? handlers : Collections.emptyList());
    }

    // ======================== WebSocket 端点注册 ========================

    /**
     * 注册 WebSocket 配置器 Bean，负责将处理器和拦截器绑定到指定端点路径。
     *
     * <p>Spring WebSocket 基础设施（通过 {@link EnableWebSocket} 引入）会自动发现
     * 容器中所有 {@link WebSocketConfigurer} Bean 并调用其
     * {@code registerWebSocketHandlers} 方法完成端点注册。</p>
     *
     * <p>端点路径和跨域白名单从 {@link WebSocketProperties} 中读取。</p>
     *
     * @param properties     WebSocket 配置属性
     * @param wsHandler      默认 WebSocket 处理器
     * @param wsInterceptor  握手拦截器
     * @return WebSocketConfigurer 实例
    */
    @Bean
    @ConditionalOnMissingBean(name = "webSocketConfigurer")
    public WebSocketConfigurer webSocketConfigurer(WebSocketProperties properties,
                                                    DefaultWsHandler wsHandler,
                                                    WsHandshakeInterceptor wsInterceptor) {
        return registry -> {
            String path = properties.getPath();
            String allowedOrigins = properties.getAllowedOrigins();

            registry.addHandler(wsHandler, path)
                    .addInterceptors(wsInterceptor)
                    .setAllowedOrigins(allowedOrigins.split(","));

            log.info("WebSocket endpoint registered: path={}, allowedOrigins={}", path, allowedOrigins);
        };
    }
}

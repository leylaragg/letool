package io.github.leylaragg.letool.websocket.config;

import io.github.leylaragg.letool.websocket.auth.PrincipalWsAuthenticator;
import io.github.leylaragg.letool.websocket.auth.WsAuthenticator;
import io.github.leylaragg.letool.websocket.auth.WsHandshakeInterceptor;
import io.github.leylaragg.letool.tool.json.Fastjson2JsonCodec;
import io.github.leylaragg.letool.tool.json.JsonCodec;
import io.github.leylaragg.letool.websocket.core.WsMessageCodec;
import io.github.leylaragg.letool.websocket.core.WsSessionManager;
import io.github.leylaragg.letool.websocket.core.WsTemplate;
import io.github.leylaragg.letool.websocket.handler.DefaultWsErrorHandler;
import io.github.leylaragg.letool.websocket.handler.DefaultWsHandler;
import io.github.leylaragg.letool.websocket.handler.DefaultWsMessageRouter;
import io.github.leylaragg.letool.websocket.handler.WsErrorHandler;
import io.github.leylaragg.letool.websocket.handler.WsMessageHandler;
import io.github.leylaragg.letool.websocket.handler.WsMessageRouteRegistrar;
import io.github.leylaragg.letool.websocket.handler.WsMessageRouter;
import io.github.leylaragg.letool.websocket.heartbeat.HeartbeatDetector;
import io.github.leylaragg.letool.websocket.room.WsRoomManager;
import jakarta.websocket.WebSocketContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

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
 *   <li>{@code letoolWebSocketConfigurer} — 端点注册器（由 Spring WebSocket 自动发现并调用）</li>
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
     * @param properties WebSocket 配置
     * @return WsSessionManager 单例实例
     */
    @Bean
    @ConditionalOnMissingBean
    public WsSessionManager wsSessionManager(WebSocketProperties properties) {
        log.debug("创建 WebSocket 会话管理器");
        return new WsSessionManager(properties.getMaxSessionPerUser());
    }

    /**
     * 创建 WebSocket 消息发送模板 Bean。
     *
     * @param sessionManager 会话管理器
     * @param roomManager    房间管理器
     * @param messageCodec 消息编解码器
     * @return WsTemplate 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public WsTemplate wsTemplate(
            WsSessionManager sessionManager,
            WsRoomManager roomManager,
            WsMessageCodec messageCodec) {
        log.debug("创建 WebSocket 消息发送模板");
        return new WsTemplate(sessionManager, roomManager, messageCodec);
    }

    /**
     * 创建 WebSocket 房间管理器 Bean。
     *
     * @param sessionManager 会话管理器
     * @param messageCodec 消息编解码器
     * @return WsRoomManager 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public WsRoomManager wsRoomManager(
            WsSessionManager sessionManager,
            WsMessageCodec messageCodec) {
        log.debug("创建 WebSocket 房间管理器");
        return new WsRoomManager(sessionManager, messageCodec);
    }

    /**
     * 创建使用应用 JSON 扩展的消息编解码器。
     *
     * @param jsonCodecProvider 应用提供的 JSON 编解码器
     * @return WebSocket 消息编解码器
     */
    @Bean
    @ConditionalOnMissingBean
    public WsMessageCodec wsMessageCodec(ObjectProvider<JsonCodec> jsonCodecProvider) {
        JsonCodec jsonCodec = jsonCodecProvider.getIfAvailable(Fastjson2JsonCodec::createDefault);
        return new WsMessageCodec(jsonCodec);
    }

    /**
     * 创建默认消息路由器并注册程序化处理器。
     *
     * @param handlers 程序化处理器
     * @return 消息路由器
     */
    @Bean
    @ConditionalOnMissingBean
    public WsMessageRouter wsMessageRouter(ObjectProvider<WsMessageHandler> handlers) {
        return new DefaultWsMessageRouter(handlers.orderedStream().toList());
    }

    /**
     * 创建注解消息路由注册器。
     *
     * @param messageRouter 消息路由器
     * @param beanFactory Spring Bean 工厂
     * @return 注解消息路由注册器
     */
    @Bean
    @ConditionalOnMissingBean
    public WsMessageRouteRegistrar wsMessageRouteRegistrar(
            WsMessageRouter messageRouter,
            ConfigurableListableBeanFactory beanFactory) {
        return new WsMessageRouteRegistrar(messageRouter, beanFactory);
    }

    /**
     * 创建安全错误响应处理器。
     *
     * @param messageCodec 消息编解码器
     * @return 错误处理器
     */
    @Bean
    @ConditionalOnMissingBean
    public WsErrorHandler wsErrorHandler(WsMessageCodec messageCodec) {
        return new DefaultWsErrorHandler(messageCodec);
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
     * @param roomManager 房间管理器
     * @param taskScheduler 心跳任务调度器
     * @return HeartbeatDetector 实例
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "letool.websocket.heartbeat", name = "enabled", havingValue = "true", matchIfMissing = true)
    public HeartbeatDetector heartbeatDetector(
            WebSocketProperties properties,
            WsSessionManager sessionManager,
            WsRoomManager roomManager,
            @Qualifier("letoolWebSocketTaskScheduler") TaskScheduler taskScheduler) {
        log.debug("创建 WebSocket 心跳检测器");
        return new HeartbeatDetector(properties, sessionManager, roomManager, taskScheduler);
    }

    /**
     * 创建 WebSocket 心跳专用任务调度器。
     *
     * <p>独立线程池可避免占用业务调度任务，并由 Spring 容器负责优雅关闭。</p>
     *
     * @return 心跳任务调度器
     */
    @Bean(name = "letoolWebSocketTaskScheduler", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "letoolWebSocketTaskScheduler")
    @ConditionalOnProperty(prefix = "letool.websocket.heartbeat", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ThreadPoolTaskScheduler letoolWebSocketTaskScheduler() {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(1);
        taskScheduler.setThreadNamePrefix("letool-ws-heartbeat-");
        taskScheduler.setWaitForTasksToCompleteOnShutdown(true);
        taskScheduler.setAwaitTerminationSeconds(5);
        return taskScheduler;
    }

    /**
     * 将应用层帧大小配置同步到底层 Jakarta WebSocket 容器。
     *
     * <p>容器限制会在消息进入应用前生效，避免超大文本帧先占用内存后才被处理器拒绝。
     * 业务自行提供 {@link WebSocketContainer} Bean 时，starter 会退让。</p>
     *
     * @param properties WebSocket 配置
     * @return Servlet WebSocket 容器配置工厂
     */
    @Bean(name = "letoolWebSocketContainer")
    @ConditionalOnMissingBean(WebSocketContainer.class)
    public ServletServerContainerFactoryBean letoolWebSocketContainer(WebSocketProperties properties) {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(Math.toIntExact(properties.getMaxFrameSize().toBytes()));
        return container;
    }

    /**
     * 创建握手拦截器 Bean。
     *
     * @return 默认 HTTP 主体认证器
     */
    @Bean
    @ConditionalOnMissingBean
    public WsAuthenticator wsAuthenticator() {
        return new PrincipalWsAuthenticator();
    }

    /**
     * 创建握手拦截器 Bean。
     *
     * @param properties WebSocket 配置属性
     * @param authenticator 握手认证器
     * @return WsHandshakeInterceptor 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public WsHandshakeInterceptor wsHandshakeInterceptor(
            WebSocketProperties properties,
            WsAuthenticator authenticator) {
        log.debug("创建 WebSocket 握手拦截器");
        return new WsHandshakeInterceptor(properties, authenticator);
    }

    /**
     * 创建默认 WebSocket 处理器 Bean。
     *
     * <p>查找所有实现了 {@link WsMessageHandler} 接口的 Bean，
     * 并自动注册到处理器中。如果未找到任何自定义处理器，则使用空列表。</p>
     *
     * @param sessionManager    会话管理器
     * @param roomManager       房间管理器
     * @param heartbeatDetectorProvider 心跳检测器提供器，心跳关闭时返回空
     * @param messageRouter     消息路由器
     * @param messageCodec      消息编解码器
     * @param errorHandler      错误处理器
     * @param properties        WebSocket 配置
     * @return DefaultWsHandler 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public DefaultWsHandler defaultWsHandler(WsSessionManager sessionManager,
                                              WsRoomManager roomManager,
                                              ObjectProvider<HeartbeatDetector> heartbeatDetectorProvider,
                                              WsMessageRouter messageRouter,
                                              WsMessageCodec messageCodec,
                                              WsErrorHandler errorHandler,
                                              WebSocketProperties properties) {
        log.debug("创建 WebSocket 默认连接处理器");
        return new DefaultWsHandler(
                sessionManager, roomManager, heartbeatDetectorProvider.getIfAvailable(), messageRouter,
                messageCodec, errorHandler, properties);
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
    @ConditionalOnMissingBean(name = "letoolWebSocketConfigurer")
    public WebSocketConfigurer letoolWebSocketConfigurer(WebSocketProperties properties,
                                                          DefaultWsHandler wsHandler,
                                                          WsHandshakeInterceptor wsInterceptor) {
        return registry -> {
            String path = properties.getPath();
            WebSocketHandlerRegistration registration = registry.addHandler(wsHandler, path)
                    .addInterceptors(wsInterceptor);
            if (!properties.getAllowedOrigins().isEmpty()) {
                registration.setAllowedOrigins(properties.getAllowedOrigins().toArray(String[]::new));
            }
            log.info("WebSocket 端点已注册，path={}，allowedOrigins={}",
                    path, properties.getAllowedOrigins().isEmpty() ? "same-origin" : properties.getAllowedOrigins());
        };
    }
}

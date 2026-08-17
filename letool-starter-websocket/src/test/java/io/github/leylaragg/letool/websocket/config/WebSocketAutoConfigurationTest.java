package io.github.leylaragg.letool.websocket.config;

import io.github.leylaragg.letool.websocket.annotation.WsMessageMapping;
import io.github.leylaragg.letool.websocket.auth.WsHandshakeInterceptor;
import io.github.leylaragg.letool.tool.json.JsonCodec;
import io.github.leylaragg.letool.websocket.core.WsMessage;
import io.github.leylaragg.letool.websocket.core.WsMessageCodec;
import io.github.leylaragg.letool.websocket.core.WsSession;
import io.github.leylaragg.letool.websocket.core.WsSessionManager;
import io.github.leylaragg.letool.websocket.core.WsTemplate;
import io.github.leylaragg.letool.websocket.handler.DefaultWsHandler;
import io.github.leylaragg.letool.websocket.handler.WsMessageRouter;
import io.github.leylaragg.letool.websocket.heartbeat.HeartbeatDetector;
import io.github.leylaragg.letool.websocket.room.WsRoomManager;
import jakarta.websocket.server.ServerContainer;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link WebSocketAutoConfiguration} 的自动装配契约测试。
 *
 * <p>固定 WebSocket starter 的 Web 环境边界、功能开关、生命周期 Bean 和用户自定义 Bean 退让行为。</p>
 */
class WebSocketAutoConfigurationTest {

    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebSocketAutoConfiguration.class))
            .withInitializer(context -> ((ConfigurableWebApplicationContext) context)
                    .getServletContext().setAttribute(
                            ServerContainer.class.getName(), mock(ServerContainer.class)))
            .withPropertyValues("spring.main.allow-bean-definition-overriding=false");

    private final ApplicationContextRunner nonWebContextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebSocketAutoConfiguration.class));

    /**
     * 验证 Web 应用默认会注册 WebSocket 核心组件。
     */
    @Test
    void shouldCreateDefaultWebSocketBeansInWebApplication() {
        webContextRunner.run(context -> {
            assertThat(context).hasSingleBean(WsSessionManager.class);
            assertThat(context).hasSingleBean(WsTemplate.class);
            assertThat(context).hasSingleBean(WsRoomManager.class);
            assertThat(context).hasSingleBean(HeartbeatDetector.class);
            assertThat(context).hasSingleBean(WsHandshakeInterceptor.class);
            assertThat(context).hasSingleBean(DefaultWsHandler.class);
            assertThat(context).hasSingleBean(WebSocketConfigurer.class);
            assertThat(context).hasSingleBean(WebSocketProperties.class);
            assertThat(context).hasBean("&letoolWebSocketContainer");
            assertThat(context.getBean(HeartbeatDetector.class).isRunning()).isTrue();
        });
    }

    /**
     * 验证非 Web 应用不会启动 WebSocket 相关 Bean。
     */
    @Test
    void shouldNotCreateWebSocketBeansInNonWebApplication() {
        nonWebContextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(WsSessionManager.class);
            assertThat(context).doesNotHaveBean(WebSocketConfigurer.class);
        });
    }

    /**
     * 验证显式关闭整个模块时不会创建 WebSocket Bean。
     */
    @Test
    void shouldNotCreateWebSocketBeansWhenDisabled() {
        webContextRunner
                .withPropertyValues("letool.websocket.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(WsSessionManager.class);
                    assertThat(context).doesNotHaveBean(WebSocketConfigurer.class);
                });
    }

    /**
     * 验证关闭心跳开关时不会创建 HeartbeatDetector，其余核心组件仍可使用。
     */
    @Test
    void shouldNotCreateHeartbeatDetectorWhenDisabled() {
        webContextRunner
                .withPropertyValues("letool.websocket.heartbeat.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(DefaultWsHandler.class);
                    assertThat(context).doesNotHaveBean(HeartbeatDetector.class);
                });
    }

    /**
     * 验证业务项目提供会话管理器时自动配置会退让，并复用该管理器创建默认工具 Bean。
     */
    @Test
    void shouldBackOffWhenUserProvidesSessionManager() {
        webContextRunner
                .withUserConfiguration(UserSessionManagerConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(WsSessionManager.class);
                    assertThat(context.getBean(WsSessionManager.class))
                            .isSameAs(context.getBean("wsSessionManager"));
                    assertThat(context.getBean(WsTemplate.class).getSessionManager())
                            .isSameAs(context.getBean("wsSessionManager"));
                });
    }

    /**
     * 验证业务项目用同名 WebSocketConfigurer 接管端点注册时，starter 会退让。
     */
    @Test
    void shouldBackOffWhenUserProvidesWebSocketConfigurerBean() {
        webContextRunner
                .withUserConfiguration(UserWebSocketConfigurerConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(WebSocketConfigurer.class);
                    assertThat(context.getBean(WebSocketConfigurer.class))
                            .isSameAs(context.getBean("letoolWebSocketConfigurer"));
                });
    }

    /**
     * 验证危险的容量配置会在应用启动阶段直接失败。
     */
    @Test
    void shouldRejectInvalidCapacityConfigurationAtStartup() {
        webContextRunner
                .withPropertyValues("letool.websocket.max-frame-size=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("WebSocket 最大消息大小必须大于 0 字节");
                });
    }

    /**
     * 验证业务 Bean 上的消息映射会在应用启动阶段自动注册。
     */
    @Test
    void shouldAutoRegisterAnnotatedMessageRoute() {
        webContextRunner
                .withUserConfiguration(AnnotatedEndpointConfiguration.class)
                .run(context -> assertThat(context.getBean(WsMessageRouter.class)
                        .getRegisteredMessageTypes()).contains("auto-route"));
    }

    /**
     * 验证业务提供的 JSON 编解码器会贯穿 WebSocket 结构化负载入口。
     */
    @Test
    void shouldUseApplicationJsonCodecForStructuredPayload() {
        JsonCodec jsonCodec = mock(JsonCodec.class);
        when(jsonCodec.write(any())).thenReturn("custom-json");

        webContextRunner
                .withBean(JsonCodec.class, () -> jsonCodec)
                .run(context -> assertThat(context.getBean(WsMessageCodec.class)
                        .create("custom", Map.of("value", 1)).getPayload()).isEqualTo("custom-json"));
    }

    /**
     * 模拟业务项目自行提供会话管理器。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserSessionManagerConfiguration {

        @Bean
        WsSessionManager wsSessionManager() {
            return new WsSessionManager();
        }
    }

    /**
     * 模拟业务项目自行接管 WebSocket 端点注册。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserWebSocketConfigurerConfiguration {

        @Bean(name = "letoolWebSocketConfigurer")
        WebSocketConfigurer webSocketConfigurer() {
            return registry -> {
            };
        }
    }

    /**
     * 模拟业务项目声明注解消息端点。
     */
    @Configuration(proxyBeanMethods = false)
    static class AnnotatedEndpointConfiguration {

        /**
         * 创建注解消息端点。
         *
         * @return 注解消息端点
         */
        @Bean
        AnnotatedEndpointContract annotatedEndpoint() {
            ProxyFactory proxyFactory = new ProxyFactory(new AnnotatedEndpoint());
            proxyFactory.setInterfaces(AnnotatedEndpointContract.class);
            return (AnnotatedEndpointContract) proxyFactory.getProxy();
        }
    }

    /**
     * 让测试端点通过 JDK 动态代理暴露的业务接口。
     */
    interface AnnotatedEndpointContract {

        /**
         * 处理自动注册测试消息。
         *
         * @param session 当前会话
         * @param message 入站消息
         */
        void handle(WsSession session, WsMessage message);
    }

    /**
     * 用于验证自动路由扫描的业务端点。
     */
    static final class AnnotatedEndpoint implements AnnotatedEndpointContract {

        /**
         * 处理自动注册测试消息。
         *
         * @param session 当前会话
         * @param message 入站消息
         */
        @WsMessageMapping("auto-route")
        @Override
        public void handle(WsSession session, WsMessage message) {
            // 自动装配测试只验证启动期路由注册，无需执行业务逻辑。
        }
    }
}

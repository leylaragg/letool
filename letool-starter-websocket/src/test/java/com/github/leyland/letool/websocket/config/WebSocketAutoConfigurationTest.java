package com.github.leyland.letool.websocket.config;

import com.github.leyland.letool.websocket.auth.WsHandshakeInterceptor;
import com.github.leyland.letool.websocket.core.WsSessionManager;
import com.github.leyland.letool.websocket.core.WsTemplate;
import com.github.leyland.letool.websocket.handler.DefaultWsHandler;
import com.github.leyland.letool.websocket.heartbeat.HeartbeatDetector;
import com.github.leyland.letool.websocket.room.WsRoomManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link WebSocketAutoConfiguration} 的自动装配契约测试。
 *
 * <p>固定 WebSocket starter 的 Web 环境边界、功能开关、生命周期 Bean 和用户自定义 Bean 退让行为。</p>
 */
class WebSocketAutoConfigurationTest {

    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebSocketAutoConfiguration.class))
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
                            .isSameAs(context.getBean("webSocketConfigurer"));
                });
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

        @Bean
        WebSocketConfigurer webSocketConfigurer() {
            return registry -> {
            };
        }
    }
}

package com.github.leyland.letool.net.config;

import com.github.leyland.letool.net.gateway.NetGateway;
import com.github.leyland.letool.net.http.NetHttpTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link NetAutoConfiguration} 的自动装配契约测试。
 *
 * <p>固定网络工具 starter 的默认轻量化、功能开关和业务项目自定义 Bean 退让行为。</p>
 */
class NetAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(NetAutoConfiguration.class))
            .withPropertyValues("spring.main.allow-bean-definition-overriding=false");

    /**
     * 验证默认配置下只绑定属性，不主动创建网络运行时 Bean。
     */
    @Test
    void shouldOnlyBindPropertiesByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(NetGateway.class);
            assertThat(context).doesNotHaveBean(NetHttpTemplate.class);
            assertThat(context).hasSingleBean(NetProperties.class);
            assertThat(context.getBean(NetProperties.class).getGateway().isEnabled()).isFalse();
            assertThat(context.getBean(NetProperties.class).getHttp().isEnabled()).isFalse();
        });
    }

    /**
     * 验证显式开启网关和 HTTP 客户端时会注册对应 Bean。
     */
    @Test
    void shouldCreateNetworkBeansWhenExplicitlyEnabled() {
        contextRunner
                .withPropertyValues(
                        "letool.net.gateway.enabled=true",
                        "letool.net.http.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(NetGateway.class);
                    assertThat(context).hasSingleBean(NetHttpTemplate.class);
                    assertThat(context).hasSingleBean(NetProperties.class);
                });
    }

    /**
     * 验证关闭网关开关时不会创建 NetGateway。
     */
    @Test
    void shouldNotCreateGatewayWhenDisabled() {
        contextRunner
                .withPropertyValues("letool.net.gateway.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(NetGateway.class);
                    assertThat(context).doesNotHaveBean(NetHttpTemplate.class);
                });
    }

    /**
     * 验证关闭 HTTP 开关时不会创建 NetHttpTemplate。
     */
    @Test
    void shouldNotCreateHttpTemplateWhenDisabled() {
        contextRunner
                .withPropertyValues("letool.net.http.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(NetGateway.class);
                    assertThat(context).doesNotHaveBean(NetHttpTemplate.class);
                });
    }

    /**
     * 验证业务项目自行提供网络基础设施 Bean 时自动配置会退让。
     */
    @Test
    void shouldBackOffWhenUserProvidesNetworkBeans() {
        contextRunner
                .withUserConfiguration(UserNetConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(NetGateway.class);
                    assertThat(context).hasSingleBean(NetHttpTemplate.class);
                    assertThat(context.getBean(NetGateway.class))
                            .isSameAs(context.getBean("netGateway"));
                    assertThat(context.getBean(NetHttpTemplate.class))
                            .isSameAs(context.getBean("netHttpTemplate"));
                });
    }

    /**
     * 模拟业务项目自行接管网络网关和 HTTP 模板。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserNetConfiguration {

        @Bean
        NetGateway netGateway() {
            return new NetGateway();
        }

        @Bean
        NetHttpTemplate netHttpTemplate() {
            return new NetHttpTemplate(100, 100);
        }
    }
}

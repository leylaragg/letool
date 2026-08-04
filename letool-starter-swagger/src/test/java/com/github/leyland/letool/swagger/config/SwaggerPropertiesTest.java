package com.github.leyland.letool.swagger.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * {@link SwaggerProperties} 最终公开配置契约测试。
 */
@DisplayName("Swagger 最终配置属性契约")
class SwaggerPropertiesTest {

    /** 配置属性真实绑定测试运行器。 */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesBindingConfiguration.class);

    /**
     * 验证 Spring Boot 将全部有效配置绑定到最终属性模型。
     */
    @Test
    @DisplayName("Spring Boot 绑定全部有效配置")
    void shouldBindAllEffectiveProperties() {
        contextRunner
                .withPropertyValues(
                        "letool.swagger.title=绑定测试 API",
                        "letool.swagger.description=绑定测试接口文档",
                        "letool.swagger.version=8.1.0",
                        "letool.swagger.contact.name=测试团队",
                        "letool.swagger.contact.email=test@example.com",
                        "letool.swagger.contact.url=https://example.com/team",
                        "letool.swagger.security.bearer-token=true")
                .run(context -> {
                    SwaggerProperties properties = context.getBean(SwaggerProperties.class);

                    assertThat(properties.getTitle()).isEqualTo("绑定测试 API");
                    assertThat(properties.getDescription()).isEqualTo("绑定测试接口文档");
                    assertThat(properties.getVersion()).isEqualTo("8.1.0");
                    assertThat(properties.getContact().getName()).isEqualTo("测试团队");
                    assertThat(properties.getContact().getEmail()).isEqualTo("test@example.com");
                    assertThat(properties.getContact().getUrl()).isEqualTo("https://example.com/team");
                    assertThat(properties.getSecurity().isBearerToken()).isTrue();
                });
    }

    /**
     * 验证基础文档信息使用稳定默认值。
     */
    @Test
    @DisplayName("基础文档信息具有稳定默认值")
    void shouldProvideDocumentDefaults() {
        SwaggerProperties properties = new SwaggerProperties();

        assertThat(properties.getTitle()).isEqualTo("API Documentation");
        assertThat(properties.getDescription()).isEmpty();
        assertThat(properties.getVersion()).isEqualTo("1.0.0");
    }

    /**
     * 验证联系人和安全配置始终提供可用的默认对象。
     */
    @Test
    @DisplayName("嵌套配置默认非空")
    void shouldProvideNonNullNestedProperties() {
        SwaggerProperties properties = new SwaggerProperties();

        assertThat(properties.getContact()).isNotNull();
        assertThat(properties.getSecurity()).isNotNull();
    }

    /**
     * 验证 Bearer 安全方案需要由使用方显式开启。
     */
    @Test
    @DisplayName("Bearer 安全方案默认关闭")
    void shouldDisableBearerByDefault() {
        SwaggerProperties properties = new SwaggerProperties();

        assertThat(properties.getSecurity().isBearerToken()).isFalse();
    }

    /**
     * 验证主属性不再暴露无效的旧开关与伪配置。
     */
    @Test
    @DisplayName("主属性删除旧开关和伪配置")
    void shouldRemoveLegacyTopLevelProperties() {
        assertThat(Arrays.stream(SwaggerProperties.class.getDeclaredFields())
                .map(Field::getName))
                .doesNotContain("enabled", "knife4j", "groups");
    }

    /**
     * 验证属性模型不再保留 Knife4j 与伪分组嵌套类型。
     */
    @Test
    @DisplayName("属性模型删除旧嵌套类型")
    void shouldRemoveLegacyNestedTypes() {
        assertThat(Arrays.stream(SwaggerProperties.class.getDeclaredClasses())
                .map(Class::getSimpleName))
                .doesNotContain("Knife4j", "Group");
    }

    /**
     * 验证标准 HTTP Bearer 配置不再镜像请求头名称。
     */
    @Test
    @DisplayName("安全配置删除请求头名称")
    void shouldRemoveLegacySecurityHeaderName() {
        assertThat(Arrays.stream(SwaggerProperties.Security.class.getDeclaredFields())
                .map(Field::getName))
                .doesNotContain("headerName");
    }

    /**
     * 验证联系人嵌套对象不能被替换为 {@code null}。
     */
    @Test
    @DisplayName("联系人配置拒绝空对象")
    void shouldRejectNullContact() {
        SwaggerProperties properties = new SwaggerProperties();

        assertThatNullPointerException()
                .isThrownBy(() -> properties.setContact(null));
    }

    /**
     * 验证安全嵌套对象不能被替换为 {@code null}。
     */
    @Test
    @DisplayName("安全配置拒绝空对象")
    void shouldRejectNullSecurity() {
        SwaggerProperties properties = new SwaggerProperties();

        assertThatNullPointerException()
                .isThrownBy(() -> properties.setSecurity(null));
    }

    /**
     * 仅注册最终 Swagger 配置属性以验证真实 Spring Boot 绑定。
     */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SwaggerProperties.class)
    static class PropertiesBindingConfiguration {
    }
}

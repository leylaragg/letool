package com.github.leyland.letool.swagger.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SwaggerAutoConfiguration} 最终自动装配契约测试。
 */
@DisplayName("Swagger 最终自动装配契约")
class SwaggerAutoConfigurationTest {

    /** Servlet Web 应用上下文测试运行器。 */
    private final WebApplicationContextRunner webContextRunner =
            new WebApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(SwaggerAutoConfiguration.class));

    /** 普通非 Web 应用上下文测试运行器。 */
    private final ApplicationContextRunner applicationContextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(SwaggerAutoConfiguration.class));

    /**
     * 验证配置属性完整映射为 OpenAPI 文档信息与联系人。
     */
    @Test
    @DisplayName("Servlet 应用创建完整文档信息")
    void shouldCreateCompleteInfoForServletApplication() {
        webContextRunner
                .withPropertyValues(
                        "letool.swagger.title=Letool 接口",
                        "letool.swagger.description=Letool 服务接口文档",
                        "letool.swagger.version=3.2.1",
                        "letool.swagger.contact.name=开发团队",
                        "letool.swagger.contact.email=dev@example.com",
                        "letool.swagger.contact.url=https://example.com")
                .run(context -> {
                    assertThat(context).hasSingleBean(OpenAPI.class);

                    Info info = context.getBean(OpenAPI.class).getInfo();
                    assertThat(info).isNotNull();
                    assertThat(info.getTitle()).isEqualTo("Letool 接口");
                    assertThat(info.getDescription()).isEqualTo("Letool 服务接口文档");
                    assertThat(info.getVersion()).isEqualTo("3.2.1");

                    Contact contact = info.getContact();
                    assertThat(contact).isNotNull();
                    assertThat(contact.getName()).isEqualTo("开发团队");
                    assertThat(contact.getEmail()).isEqualTo("dev@example.com");
                    assertThat(contact.getUrl()).isEqualTo("https://example.com");
                });
    }

    /**
     * 验证默认配置不会声明安全方案或全局安全要求。
     */
    @Test
    @DisplayName("Bearer 安全方案默认关闭")
    void shouldDisableBearerSecurityByDefault() {
        webContextRunner.run(context -> {
            OpenAPI openAPI = context.getBean(OpenAPI.class);
            Map<String, SecurityScheme> securitySchemes = openAPI.getComponents() == null
                    ? null
                    : openAPI.getComponents().getSecuritySchemes();

            assertThat(securitySchemes).isNullOrEmpty();
            assertThat(openAPI.getSecurity()).isNullOrEmpty();
        });
    }

    /**
     * 验证联系人字段全部为空白时不会向最终 OpenAPI 文档写入空对象。
     */
    @Test
    @DisplayName("空白联系人不写入 OpenAPI 文档")
    void shouldOmitContactWhenAllContactFieldsAreBlank() {
        webContextRunner
                .withPropertyValues(
                        "letool.swagger.contact.name=   ",
                        "letool.swagger.contact.email=   ",
                        "letool.swagger.contact.url=   ")
                .run(context -> {
                    OpenAPI openAPI = context.getBean(OpenAPI.class);

                    assertThat(openAPI.getInfo()).isNotNull();
                    assertThat(openAPI.getInfo().getContact()).isNull();
                });
    }

    /**
     * 验证显式开启后创建固定名称的 HTTP Bearer JWT 安全方案和全局要求。
     */
    @Test
    @DisplayName("显式开启 Bearer 后创建标准安全方案")
    void shouldCreateBearerSecurityWhenExplicitlyEnabled() {
        webContextRunner
                .withPropertyValues("letool.swagger.security.bearer-token=true")
                .run(context -> {
                    OpenAPI openAPI = context.getBean(OpenAPI.class);
                    assertThat(openAPI.getComponents()).isNotNull();
                    assertThat(openAPI.getComponents().getSecuritySchemes())
                            .containsOnlyKeys("BearerAuth");

                    SecurityScheme scheme = openAPI.getComponents()
                            .getSecuritySchemes()
                            .get("BearerAuth");
                    assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
                    assertThat(scheme.getScheme()).isEqualTo("bearer");
                    assertThat(scheme.getBearerFormat()).isEqualTo("JWT");

                    assertThat(openAPI.getSecurity())
                            .singleElement()
                            .satisfies(requirement -> {
                                assertThat(requirement).containsOnlyKeys("BearerAuth");
                                assertThat(requirement.get("BearerAuth")).isEmpty();
                            });
                });
    }

    /**
     * 验证 Springdoc 原生 API 文档开关关闭时 Letool 不创建文档对象。
     */
    @Test
    @DisplayName("原生 API 文档关闭时不创建 Letool 文档对象")
    void shouldDisableLetoolOpenApiWhenNativeApiDocsAreDisabled() {
        webContextRunner
                .withPropertyValues("springdoc.api-docs.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(OpenAPI.class));
    }

    /**
     * 验证 OpenAPI 核心类型不在类路径时自动配置不会注册文档对象。
     */
    @Test
    @DisplayName("OpenAPI 核心类型缺失时不装配")
    void shouldNotConfigureWhenOpenApiClassIsMissing() {
        webContextRunner
                .withClassLoader(new FilteredClassLoader(OpenAPI.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean("letoolOpenApi");
                    assertThat(context).doesNotHaveBean("letoolOpenAPI");
                });
    }

    /**
     * 验证用户提供文档对象时自动配置完整退让并保留用户内容。
     */
    @Test
    @DisplayName("用户 OpenAPI 对象存在时自动配置退让")
    void shouldBackOffWhenUserProvidesOpenApiBean() {
        webContextRunner
                .withUserConfiguration(UserOpenApiConfiguration.class)
                .run(context -> {
                    OpenAPI customOpenApi = context.getBean("customOpenApi", OpenAPI.class);

                    assertThat(context).hasSingleBean(OpenAPI.class);
                    assertThat(context.getBean(OpenAPI.class)).isSameAs(customOpenApi);
                    assertThat(customOpenApi.getInfo().getTitle()).isEqualTo("用户文档");
                });
    }

    /**
     * 验证普通非 Servlet 应用不会创建 Letool 文档对象。
     */
    @Test
    @DisplayName("非 Servlet 应用不创建 Letool 文档对象")
    void shouldNotCreateOpenApiOutsideServletApplication() {
        applicationContextRunner.run(context ->
                assertThat(context).doesNotHaveBean(OpenAPI.class));
    }

    /**
     * 验证 Letool 不再伪造默认 Springdoc 分组。
     */
    @Test
    @DisplayName("默认不创建 Springdoc 分组")
    void shouldNotCreateDefaultGroupedOpenApi() {
        webContextRunner.run(context ->
                assertThat(context).doesNotHaveBean(GroupedOpenApi.class));
    }

    /**
     * 模拟业务应用自行提供 OpenAPI 文档对象。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserOpenApiConfiguration {

        /**
         * 创建带可识别标题的用户文档对象。
         *
         * @return 用户接管的 OpenAPI 文档对象
         */
        @Bean
        OpenAPI customOpenApi() {
            return new OpenAPI().info(new Info().title("用户文档"));
        }
    }
}

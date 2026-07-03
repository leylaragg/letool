package com.github.leyland.letool.swagger.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SwaggerAutoConfiguration} 的自动装配契约测试。
 *
 * <p>固定 Swagger starter 在工具包场景下的启停、默认 Bean 创建以及用户自定义 Bean 退让行为。</p>
 */
class SwaggerAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SwaggerAutoConfiguration.class))
            .withPropertyValues("spring.main.allow-bean-definition-overriding=false");

    /**
     * 验证默认启用时会创建 OpenAPI 文档信息和默认分组。
     */
    @Test
    void shouldCreateDefaultSwaggerBeansWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "letool.swagger.title=Letool API",
                        "letool.swagger.version=2.0.0")
                .run(context -> {
                    assertThat(context).hasSingleBean(OpenAPI.class);
                    assertThat(context).hasSingleBean(GroupedOpenApi.class);

                    OpenAPI openAPI = context.getBean(OpenAPI.class);
                    assertThat(openAPI.getInfo().getTitle()).isEqualTo("Letool API");
                    assertThat(openAPI.getInfo().getVersion()).isEqualTo("2.0.0");

                    GroupedOpenApi groupedOpenApi = context.getBean(GroupedOpenApi.class);
                    assertThat(groupedOpenApi.getGroup()).isEqualTo("default");
                    assertThat(groupedOpenApi.getPackagesToScan()).containsExactly("com.github.leyland");
                });
    }

    /**
     * 验证显式关闭 starter 时不会创建 Swagger 文档 Bean。
     */
    @Test
    void shouldNotCreateSwaggerBeansWhenDisabled() {
        contextRunner
                .withPropertyValues("letool.swagger.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(OpenAPI.class);
                    assertThat(context).doesNotHaveBean(GroupedOpenApi.class);
                });
    }

    /**
     * 验证用户接管 OpenAPI Bean 时自动配置会按类型退让。
     */
    @Test
    void shouldBackOffWhenUserProvidesOpenApiBean() {
        contextRunner
                .withUserConfiguration(UserOpenApiConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(OpenAPI.class);
                    assertThat(context.getBean(OpenAPI.class))
                            .isSameAs(context.getBean("customOpenAPI"));
                });
    }

    /**
     * 验证用户接管默认分组 Bean 时自动配置会按稳定 Bean 名称退让。
     */
    @Test
    void shouldBackOffWhenUserProvidesDefaultGroupApiBean() {
        contextRunner
                .withUserConfiguration(UserGroupedOpenApiConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(GroupedOpenApi.class);
                    assertThat(context.getBean(GroupedOpenApi.class))
                            .isSameAs(context.getBean("defaultGroupApi"));
                });
    }

    /**
     * 验证默认扫描包和用户配置的扫描包会合并，避免默认 letool 包被覆盖。
     */
    @Test
    void shouldMergeDefaultPackageAndConfiguredGroupPackages() {
        contextRunner
                .withPropertyValues(
                        "letool.swagger.groups[0].name=业务接口",
                        "letool.swagger.groups[0].base-package=com.example.web",
                        "letool.swagger.groups[1].name=管理接口",
                        "letool.swagger.groups[1].base-package=com.example.admin")
                .run(context -> assertThat(context.getBean(GroupedOpenApi.class).getPackagesToScan())
                        .containsExactly("com.github.leyland", "com.example.web", "com.example.admin"));
    }

    /**
     * 模拟业务项目自行提供 OpenAPI 文档对象。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserOpenApiConfiguration {

        @Bean
        OpenAPI customOpenAPI() {
            return new OpenAPI();
        }
    }

    /**
     * 模拟业务项目自行提供默认分组对象。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserGroupedOpenApiConfiguration {

        @Bean
        GroupedOpenApi defaultGroupApi() {
            return GroupedOpenApi.builder()
                    .group("custom-default")
                    .packagesToScan("com.example.custom")
                    .build();
        }
    }
}

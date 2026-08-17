package io.github.leylaragg.letool.swagger.config;

import io.github.leylaragg.letool.exception.core.SystemException;
import io.github.leylaragg.letool.swagger.exception.SwaggerErrorCode;
import io.github.leylaragg.letool.swagger.web.SwaggerEndpointBlockFilter;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Letool OpenAPI 文档自动配置。
 *
 * <p>Springdoc 负责 OpenAPI 引擎、扫描和分组，Knife4j 纯 UI 提供增强界面；
 * 该自动配置负责常用文档信息、标准 Bearer JWT 安全方案、用户 Bean 退让，
 * 以及 {@code letool.swagger.enabled} 的统一关闭语义。</p>
 *
 * @author leyland
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(SwaggerProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import({
        SwaggerAutoConfiguration.ApiDocsEnabledConfiguration.class,
        SwaggerAutoConfiguration.DisabledSwaggerConfiguration.class
})
public class SwaggerAutoConfiguration {

    /**
     * Letool Swagger 与 Springdoc API 文档均启用时的默认配置。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(OpenAPI.class)
    @ConditionalOnProperties({
            @ConditionalOnProperty(
                    prefix = "letool.swagger",
                    name = "enabled",
                    havingValue = "true",
                    matchIfMissing = true),
            @ConditionalOnProperty(
                    prefix = "springdoc.api-docs",
                    name = "enabled",
                    havingValue = "true",
                    matchIfMissing = true)
    })
    static class ApiDocsEnabledConfiguration {

        /**
         * 创建 Letool 默认的 OpenAPI 文档对象。
         *
         * <p>当业务应用已经提供 {@link OpenAPI} Bean 时，本配置自动退让，
         * 不会覆盖用户的文档定制。</p>
         *
         * @param properties Letool Swagger 配置属性
         * @return 根据配置构建的 OpenAPI 文档对象
         */
        @Bean
        @ConditionalOnMissingBean(OpenAPI.class)
        OpenAPI letoolOpenApi(SwaggerProperties properties) {
            Info info = new Info()
                    .title(properties.getTitle())
                    .description(properties.getDescription())
                    .version(properties.getVersion());

            SwaggerProperties.Contact contactProperties = properties.getContact();
            if (hasContact(contactProperties)) {
                info.contact(buildContact(contactProperties));
            }

            OpenAPI openAPI = new OpenAPI().info(info);
            if (properties.getSecurity().isBearerToken()) {
                addBearerSecurity(openAPI, properties.getSecurity());
            }
            return openAPI;
        }

        /**
         * 判断联系人配置是否包含可展示内容。
         *
         * @param contact 联系人配置
         * @return 至少一个联系人字段包含有效文本时返回 {@code true}
         */
        private boolean hasContact(SwaggerProperties.Contact contact) {
            return StringUtils.hasText(contact.getName())
                    || StringUtils.hasText(contact.getEmail())
                    || StringUtils.hasText(contact.getUrl());
        }

        /**
         * 将 Letool 联系人配置转换为 OpenAPI 联系人对象。
         *
         * <p>只复制包含有效文本的字段，避免向最终文档写入空白值。</p>
         *
         * @param properties Letool 联系人配置
         * @return OpenAPI 联系人对象
         */
        private Contact buildContact(SwaggerProperties.Contact properties) {
            Contact contact = new Contact();
            if (StringUtils.hasText(properties.getName())) {
                contact.setName(properties.getName());
            }
            if (StringUtils.hasText(properties.getEmail())) {
                contact.setEmail(properties.getEmail());
            }
            if (StringUtils.hasText(properties.getUrl())) {
                contact.setUrl(properties.getUrl());
            }
            return contact;
        }

        /**
         * 向 OpenAPI 文档添加标准 HTTP Bearer JWT 安全方案。
         *
         * @param openAPI OpenAPI 文档对象
         * @param securityProperties Letool 安全配置
         * @throws SystemException 安全方案名称为空白时抛出
         */
        private void addBearerSecurity(
                OpenAPI openAPI,
                SwaggerProperties.Security securityProperties) {
            String schemeName = securityProperties.getSchemeName();
            if (!StringUtils.hasText(schemeName)) {
                throw SystemException.of(
                        SwaggerErrorCode.CONFIGURATION_INVALID,
                        "letool.swagger.security.scheme-name 不能为空");
            }

            SecurityScheme securityScheme = new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT");
            openAPI.schemaRequirement(schemeName, securityScheme)
                    .addSecurityItem(new SecurityRequirement().addList(schemeName));
        }
    }

    /**
     * Letool Swagger 关闭状态下的端点保护配置。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
            prefix = "letool.swagger",
            name = "enabled",
            havingValue = "false")
    static class DisabledSwaggerConfiguration {

        /**
         * 注册仅拦截 API 文档入口的 Servlet Filter。
         *
         * @param apiDocsPath Springdoc OpenAPI 文档路径
         * @param swaggerUiPath Springdoc Swagger UI 兼容入口路径
         * @param servletPath Spring MVC DispatcherServlet 路径
         * @param groupedOpenApis Springdoc 真实分组提供器
         * @return Swagger 文档端点过滤器注册对象
         */
        @Bean
        @ConditionalOnMissingBean(name = "letoolSwaggerEndpointBlockFilter")
        FilterRegistrationBean<SwaggerEndpointBlockFilter>
                letoolSwaggerEndpointBlockFilter(
                        @Value("${springdoc.api-docs.path:/v3/api-docs}")
                        String apiDocsPath,
                        @Value("${springdoc.swagger-ui.path:/swagger-ui.html}")
                        String swaggerUiPath,
                        @Value("${spring.mvc.servlet.path:}")
                        String servletPath,
                        ObjectProvider<GroupedOpenApi> groupedOpenApis) {
            Set<String> groupNames = groupedOpenApis.orderedStream()
                    .map(GroupedOpenApi::getGroup)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toUnmodifiableSet());
            FilterRegistrationBean<SwaggerEndpointBlockFilter> registration =
                    new FilterRegistrationBean<>(
                            new SwaggerEndpointBlockFilter(
                                    apiDocsPath,
                                    swaggerUiPath,
                                    servletPath,
                                    groupNames));
            registration.setName("letoolSwaggerEndpointBlockFilter");
            registration.addUrlPatterns("/*");
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return registration;
        }
    }
}

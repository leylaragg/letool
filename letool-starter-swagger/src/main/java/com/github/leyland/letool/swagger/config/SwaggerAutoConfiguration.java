package com.github.leyland.letool.swagger.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * Letool OpenAPI 文档自动配置。
 *
 * <p>该自动配置仅提供文档基本信息和可选的标准 Bearer JWT 安全方案。
 * 文档端点、用户界面、扫描范围及分组策略均由 Springdoc 原生能力管理。</p>
 *
 * @author leyland
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(SwaggerProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(OpenAPI.class)
@ConditionalOnProperty(
        prefix = "springdoc.api-docs",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SwaggerAutoConfiguration {

    /** 标准 Bearer 安全方案名称。 */
    private static final String BEARER_AUTH_SCHEME = "BearerAuth";

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
    public OpenAPI letoolOpenApi(SwaggerProperties properties) {
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
            SecurityScheme securityScheme = new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT");
            openAPI.schemaRequirement(BEARER_AUTH_SCHEME, securityScheme)
                    .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH_SCHEME));
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
}

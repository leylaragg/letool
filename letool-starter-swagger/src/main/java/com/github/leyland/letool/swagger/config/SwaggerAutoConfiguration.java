package com.github.leyland.letool.swagger.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Swagger / Knife4j 自动配置类（基于 SpringDoc OpenAPI 3.x + Knife4j 4.5.0）。
 *
 * <h3>功能概述</h3>
 * <p>该类是 {@code letool-starter-swagger} 模块的核心自动配置入口，负责：</p>
 * <ol>
 *   <li><b>创建 OpenAPI Bean</b> —— 根据 {@link SwaggerProperties} 中的配置，
 *       构建 OpenAPI 规范的文档信息对象（标题、描述、版本、联系人、许可证等）。</li>
 *   <li><b>创建 API 分组 Bean</b> —— 根据配置中的分组列表，生成对应的
 *       {@link GroupedOpenApi} Bean，实现 API 文档的多分组展示。</li>
 *   <li><b>配置安全认证方案</b> —— 当开启 Bearer Token 认证时，向 OpenAPI 文档
 *       注入 JWT Bearer 安全方案，使 Swagger UI 支持在线调试需要认证的接口。</li>
 * </ol>
 *
 * <h3>激活条件</h3>
 * <p>自动配置在以下条件同时满足时生效：</p>
 * <ul>
 *   <li>Classpath 中存在 {@code io.swagger.v3.oas.models.OpenAPI} 类
 *       （即引入了 {@code springdoc-openapi-starter-webmvc-ui} 依赖）</li>
 *   <li>配置项 {@code letool.swagger.enabled} 为 {@code true}（默认为 {@code true}，
 *       即未显式设置时也会激活）</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <p>引入本 starter 后，无需任何 Java 配置代码，只需在
 * {@code application.yml} 中按需配置即可：</p>
 * <pre>{@code
 * letool:
 *   swagger:
 *     enabled: true
 *     title: "项目接口文档"
 *     description: "项目 RESTful API 接口文档"
 *     version: "1.0.0"
 *     contact:
 *       name: "开发团队"
 *       email: "dev@example.com"
 *     security:
 *       bearer-token: true
 *       header-name: "Authorization"
 *     groups:
 *       - name: "默认分组"
 *         base-package: "com.example.controller"
 * }</pre>
 *
 * <h3>技术栈</h3>
 * <ul>
 *   <li>SpringDoc OpenAPI 3.x —— 基于 OpenAPI 3.0 规范的 Spring Boot 集成方案</li>
 *   <li>Knife4j 4.5.0 —— Swagger 的增强 UI 实现，提供更友好的中文界面和增强功能</li>
 *   <li>Spring Boot AutoConfiguration —— 基于 {@code spring.factories} 的自动配置机制</li>
 * </ul>
 *
 * @author leyland
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(SwaggerProperties.class)
@ConditionalOnClass(io.swagger.v3.oas.models.OpenAPI.class)
@ConditionalOnProperty(prefix = "letool.swagger", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SwaggerAutoConfiguration {

    // ======================== 日志记录器 ========================

    /** 日志记录器，用于输出自动配置初始化信息 */
    private static final Logger log = LoggerFactory.getLogger(SwaggerAutoConfiguration.class);

    // ======================== Bean 定义 ========================

    /**
     * 创建 OpenAPI 规范的文档信息 Bean。
     *
     * <p>该 Bean 是 SpringDoc OpenAPI 框架的核心配置对象，包含以下信息：</p>
     * <ul>
     *   <li><b>Info（文档基本信息）</b>：
     *     <ul>
     *       <li>标题（{@code title}）—— 来自 {@code letool.swagger.title}</li>
     *       <li>描述（{@code description}）—— 来自 {@code letool.swagger.description}</li>
     *       <li>版本（{@code version}）—— 来自 {@code letool.swagger.version}</li>
     *       <li>联系人（{@code contact}）—— 来自 {@code letool.swagger.contact.*}</li>
     *     </ul>
     *   </li>
     *   <li><b>SecurityScheme（安全认证方案）</b>：
     *     <p>当 {@code letool.swagger.security.bearer-token} 为 {@code true} 时，
     *     自动添加一个名为 {@code "Bearer"} 的 HTTP Bearer JWT 安全方案，
     *     并将其作为全局安全要求。用户可在 Swagger UI 中点击“Authorize”
     *     按钮填入 JWT Token，后续所有接口请求将自动携带
     *     {@code Authorization: Bearer <token>} 请求头。</p>
     *   </li>
     * </ul>
     *
     * @param properties Swagger 配置属性对象，由 Spring Boot 自动绑定
     * @return 构建完成的 {@link OpenAPI} 实例，交由 Spring 容器管理
     */
    @Bean
    @ConditionalOnMissingBean(OpenAPI.class)
    public OpenAPI letoolOpenAPI(SwaggerProperties properties) {
        // ---------- 构建联系人信息 ----------
        SwaggerProperties.Contact contactProps = properties.getContact();
        Contact contact = new Contact()
                .name(contactProps.getName())
                .email(contactProps.getEmail())
                .url(contactProps.getUrl());

        // ---------- 构建文档基本信息 ----------
        Info info = new Info()
                .title(properties.getTitle())
                .description(properties.getDescription())
                .version(properties.getVersion())
                .contact(contact);

        // ---------- 创建 OpenAPI 实例 ----------
        OpenAPI openApi = new OpenAPI().info(info);

        // ---------- 配置 Bearer Token 安全认证 ----------
        if (properties.getSecurity().isBearerToken()) {
            String headerName = properties.getSecurity().getHeaderName();
            openApi.schemaRequirement("Bearer", new SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT"))
                    .addSecurityItem(new SecurityRequirement().addList("Bearer"));
        }

        log.info("Swagger auto-configuration initialized: {}", properties.getTitle());
        return openApi;
    }

    /**
     * 创建默认的 API 文档分组 Bean。
     *
     * <p>该 Bean 负责定义 API 文档的分组规则。在 Knife4j/Swagger UI 界面中，
     * 右上角的下拉菜单会列出所有已注册的 {@link GroupedOpenApi} Bean，
     * 每个分组展示对应包路径下的 API 接口文档。</p>
     *
     * <h4>分组逻辑说明</h4>
     * <ol>
     *   <li><b>默认扫描路径</b>：始终包含 {@code "com.github.leyland"} 包，
     *       确保 letool 框架内部的端点也会被文档化。</li>
     *   <li><b>自定义分组路径</b>：如果通过 {@code letool.swagger.groups}
     *       配置了一个或多个分组，则将其 {@code basePackage} 也加入扫描范围。</li>
     *   <li><b>分组命名</b>：固定为 {@code "default"}，展示名称为
     *       {@code properties.getTitle()}。</li>
     * </ol>
     *
     * <h4>配置示例</h4>
     * <pre>{@code
     * letool:
     *   swagger:
     *     groups:
     *       - name: "用户模块"
     *         base-package: "com.example.user.controller"
     *       - name: "订单模块"
     *         base-package: "com.example.order.controller"
     * }</pre>
     * <p>以上配置会将 {@code com.example.user.controller} 和
     * {@code com.example.order.controller} 两个包下的 Controller
     * 接口纳入默认分组文档中。</p>
     *
     * <h4>扩展多分组</h4>
     * <p>如需为不同分组生成独立的 {@link GroupedOpenApi} Bean（实现真正意义上的多分组切换），
     * 可在项目中手动注册额外的 Bean：</p>
     * <pre>{@code
     * @Bean
     * public GroupedOpenApi userGroupApi() {
     *     return GroupedOpenApi.builder()
     *             .group("用户模块")
     *             .packagesToScan("com.example.user.controller")
     *             .build();
     * }
     * }</pre>
     *
     * @param properties Swagger 配置属性对象，用于获取分组配置和文档标题
     * @return 构建完成的 {@link GroupedOpenApi} 实例，交由 Spring 容器管理
     */
    @Bean
    @ConditionalOnMissingBean(name = "defaultGroupApi")
    public GroupedOpenApi defaultGroupApi(SwaggerProperties properties) {
        // ---------- 构建基础分组配置 ----------
        GroupedOpenApi.Builder builder = GroupedOpenApi.builder()
                .group("default")
                .displayName(properties.getTitle());

        Set<String> packagesToScan = new LinkedHashSet<>();
        packagesToScan.add("com.github.leyland");

        // ---------- 合并自定义分组包路径 ----------
        List<SwaggerProperties.Group> groups = properties.getGroups();
        if (groups != null && !groups.isEmpty()) {
            groups.stream()
                    .map(SwaggerProperties.Group::getBasePackage)
                    .filter(basePackage -> basePackage != null && !basePackage.isBlank())
                    .map(String::trim)
                    .forEach(packagesToScan::add);
        }

        builder.packagesToScan(packagesToScan.toArray(String[]::new));
        return builder.build();
    }
}

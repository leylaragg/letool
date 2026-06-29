package com.github.leyland.letool.swagger.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Swagger / Knife4j 自动配置属性类，映射 {@code letool.swagger} 开头的配置项。
 *
 * <p>该类通过 {@link ConfigurationProperties} 将 {@code application.yml}
 * 或 {@code application.properties} 中以 {@code letool.swagger} 为前缀的
 * 配置绑定到对应的 Java 字段上，供 {@link SwaggerAutoConfiguration} 使用。</p>
 *
 * <p>支持的主要配置类别：</p>
 * <ul>
 *   <li><b>基础信息</b> —— 启用开关、文档标题、描述、版本号</li>
 *   <li><b>联系人信息</b> —— 姓名、邮箱、URL（嵌套 {@link Contact}）</li>
 *   <li><b>安全认证</b> —— Bearer Token 启用状态、请求头名称（嵌套 {@link Security}）</li>
 *   <li><b>Knife4j 增强</b> —— 离线文档、页脚定制（嵌套 {@link Knife4j}）</li>
 *   <li><b>API 分组</b> —— 多分组名称及包路径列表（嵌套 {@link Group}）</li>
 * </ul>
 *
 * <p>YAML 配置示例：</p>
 * <pre>{@code
 * letool:
 *   swagger:
 *     enabled: true
 *     title: "项目接口文档"
 *     description: "RESTful API 文档"
 *     version: "2.0.0"
 *     contact:
 *       name: "张三"
 *       email: "zhangsan@example.com"
 *       url: "https://example.com"
 *     security:
 *       bearer-token: true
 *       header-name: "Authorization"
 *     knife4j:
 *       offline-docs: false
 *       enable-footer: true
 *     groups:
 *       - name: "用户模块"
 *         base-package: "com.example.user"
 *       - name: "订单模块"
 *         base-package: "com.example.order"
 * }</pre>
 *
 * @author leyland
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "letool.swagger")
public class SwaggerProperties {

    // ======================== 基础配置字段 ========================

    /** 是否启用 Swagger 文档功能，默认 {@code true} */
    private boolean enabled = true;

    /** API 文档标题，默认 {@code "API Documentation"} */
    private String title = "API Documentation";

    /** API 文档描述信息，默认为空字符串 */
    private String description = "";

    /** API 文档版本号，默认 {@code "1.0.0"} */
    private String version = "1.0.0";

    // ======================== 嵌套对象字段 ========================

    /** 联系人信息 */
    private Contact contact = new Contact();

    /** 安全认证配置 */
    private Security security = new Security();

    /** Knife4j 增强配置 */
    private Knife4j knife4j = new Knife4j();

    /** API 分组列表 */
    private List<Group> groups = new ArrayList<>();

    // ======================== Getter / Setter ========================

    /**
     * 获取 Swagger 文档功能的启用状态。
     *
     * @return {@code true} 表示启用，{@code false} 表示禁用
     */
    public boolean isEnabled() { return enabled; }

    /**
     * 设置 Swagger 文档功能的启用状态。
     *
     * @param enabled {@code true} 启用，{@code false} 禁用
     */
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /**
     * 获取 API 文档标题。
     *
     * @return 文档标题字符串
     */
    public String getTitle() { return title; }

    /**
     * 设置 API 文档标题。
     *
     * @param title 文档标题
     */
    public void setTitle(String title) { this.title = title; }

    /**
     * 获取 API 文档描述信息。
     *
     * @return 文档描述字符串
     */
    public String getDescription() { return description; }

    /**
     * 设置 API 文档描述信息。
     *
     * @param description 文档描述
     */
    public void setDescription(String description) { this.description = description; }

    /**
     * 获取 API 文档版本号。
     *
     * @return 版本号字符串
     */
    public String getVersion() { return version; }

    /**
     * 设置 API 文档版本号。
     *
     * @param version 版本号
     */
    public void setVersion(String version) { this.version = version; }

    /**
     * 获取联系人信息对象。
     *
     * @return 联系人配置对象
     */
    public Contact getContact() { return contact; }

    /**
     * 设置联系人信息对象。
     *
     * @param contact 联系人配置对象
     */
    public void setContact(Contact contact) { this.contact = contact; }

    /**
     * 获取安全认证配置对象。
     *
     * @return 安全配置对象
     */
    public Security getSecurity() { return security; }

    /**
     * 设置安全认证配置对象。
     *
     * @param security 安全配置对象
     */
    public void setSecurity(Security security) { this.security = security; }

    /**
     * 获取 Knife4j 增强配置对象。
     *
     * @return Knife4j 配置对象
     */
    public Knife4j getKnife4j() { return knife4j; }

    /**
     * 设置 Knife4j 增强配置对象。
     *
     * @param knife4j Knife4j 配置对象
     */
    public void setKnife4j(Knife4j knife4j) { this.knife4j = knife4j; }

    /**
     * 获取 API 分组列表。
     *
     * @return 分组配置列表
     */
    public List<Group> getGroups() { return groups; }

    /**
     * 设置 API 分组列表。
     *
     * @param groups 分组配置列表
     */
    public void setGroups(List<Group> groups) { this.groups = groups; }

    // ======================== 内部类：联系人信息 ========================

    /**
     * Swagger 文档联系人信息配置。
     *
     * <p>对应 OpenAPI 规范中的 {@code info.contact} 节点，
     * 用于在 API 文档页面展示联系人姓名、邮箱和主页地址。</p>
     */
    public static class Contact {

        /** 联系人姓名 */
        private String name;

        /** 联系人邮箱 */
        private String email;

        /** 联系人主页 URL */
        private String url;

        /**
         * 获取联系人姓名。
         *
         * @return 联系人姓名
         */
        public String getName() { return name; }

        /**
         * 设置联系人姓名。
         *
         * @param name 联系人姓名
         */
        public void setName(String name) { this.name = name; }

        /**
         * 获取联系人邮箱。
         *
         * @return 联系人邮箱地址
         */
        public String getEmail() { return email; }

        /**
         * 设置联系人邮箱。
         *
         * @param email 联系人邮箱地址
         */
        public void setEmail(String email) { this.email = email; }

        /**
         * 获取联系人主页 URL。
         *
         * @return 联系人主页 URL
         */
        public String getUrl() { return url; }

        /**
         * 设置联系人主页 URL。
         *
         * @param url 联系人主页 URL
         */
        public void setUrl(String url) { this.url = url; }
    }

    // ======================== 内部类：安全认证配置 ========================

    /**
     * Swagger 安全认证配置。
     *
     * <p>用于配置 API 文档中的认证方式，当前支持 Bearer Token（JWT）模式。
     * 启用后，Swagger UI 中会出现“Authorize”按钮，用户可填入 JWT Token
     * 来测试需要认证的接口。</p>
     */
    public static class Security {

        /** 是否启用 Bearer Token 认证，默认 {@code true} */
        private boolean bearerToken = true;

        /** Bearer Token 在 HTTP 请求头中的名称，默认 {@code "Authorization"} */
        private String headerName = "Authorization";

        /**
         * 获取 Bearer Token 认证是否启用。
         *
         * @return {@code true} 表示启用，{@code false} 表示禁用
         */
        public boolean isBearerToken() { return bearerToken; }

        /**
         * 设置 Bearer Token 认证是否启用。
         *
         * @param bearerToken {@code true} 启用，{@code false} 禁用
         */
        public void setBearerToken(boolean bearerToken) { this.bearerToken = bearerToken; }

        /**
         * 获取 Bearer Token 的 HTTP 请求头名称。
         *
         * @return 请求头名称字符串
         */
        public String getHeaderName() { return headerName; }

        /**
         * 设置 Bearer Token 的 HTTP 请求头名称。
         *
         * @param headerName 请求头名称
         */
        public void setHeaderName(String headerName) { this.headerName = headerName; }
    }

    // ======================== 内部类：Knife4j 增强配置 ========================

    /**
     * Knife4j 增强功能配置。
     *
     * <p>Knife4j 是 Swagger 的增强 UI 实现，提供离线文档导出、页脚定制等
     * 额外功能。该类用于控制这些增强特性的开关。</p>
     */
    public static class Knife4j {

        /** 是否启用离线文档功能，默认 {@code false} */
        private boolean offlineDocs = false;

        /** 是否启用自定义页脚，默认 {@code false} */
        private boolean enableFooter = false;

        /**
         * 获取离线文档功能是否启用。
         *
         * @return {@code true} 表示启用，{@code false} 表示禁用
         */
        public boolean isOfflineDocs() { return offlineDocs; }

        /**
         * 设置离线文档功能是否启用。
         *
         * @param offlineDocs {@code true} 启用，{@code false} 禁用
         */
        public void setOfflineDocs(boolean offlineDocs) { this.offlineDocs = offlineDocs; }

        /**
         * 获取自定义页脚功能是否启用。
         *
         * @return {@code true} 表示启用，{@code false} 表示禁用
         */
        public boolean isEnableFooter() { return enableFooter; }

        /**
         * 设置自定义页脚功能是否启用。
         *
         * @param enableFooter {@code true} 启用，{@code false} 禁用
         */
        public void setEnableFooter(boolean enableFooter) { this.enableFooter = enableFooter; }
    }

    // ======================== 内部类：API 分组配置 ========================

    /**
     * API 文档分组配置。
     *
     * <p>用于定义一组 API 分组的名称和对应的包路径。
     * 在 Knife4j/Swagger UI 界面中，接口将按此分组展示。</p>
     */
    public static class Group {

        /** 分组名称，在文档界面中作为分组标识展示 */
        private String name;

        /** 该分组对应的 Java 包路径，用于扫描包含的 Controller */
        private String basePackage;

        /**
         * 获取分组名称。
         *
         * @return 分组名称字符串
         */
        public String getName() { return name; }

        /**
         * 设置分组名称。
         *
         * @param name 分组名称
         */
        public void setName(String name) { this.name = name; }

        /**
         * 获取分组的包路径。
         *
         * @return 包路径字符串
         */
        public String getBasePackage() { return basePackage; }

        /**
         * 设置分组的包路径。
         *
         * @param basePackage 包路径
         */
        public void setBasePackage(String basePackage) { this.basePackage = basePackage; }
    }
}

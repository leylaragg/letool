package com.github.leyland.letool.swagger.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Objects;

/**
 * Letool OpenAPI 文档便利配置。
 *
 * <p>该配置负责 Letool 文档入口开关、项目文档信息和 Bearer JWT 安全方案。
 * OpenAPI 引擎、控制器扫描与文档分组等框架能力仍由 Springdoc 原生配置负责。</p>
 *
 * @author leyland
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "letool.swagger")
public class SwaggerProperties {

    /** 是否启用 Letool API 文档入口。 */
    private boolean enabled = true;

    /** API 文档标题。 */
    private String title = "API Documentation";

    /** API 文档描述。 */
    private String description = "";

    /** API 文档版本号。 */
    private String version = "1.0.0";

    /** API 文档联系人配置。 */
    private Contact contact = new Contact();

    /** API 文档安全方案配置。 */
    private Security security = new Security();

    /**
     * 判断是否启用 Letool API 文档入口。
     *
     * @return {@code true} 表示启用，{@code false} 表示关闭
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用 Letool API 文档入口。
     *
     * @param enabled {@code true} 表示启用，{@code false} 表示关闭
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取 API 文档标题。
     *
     * @return API 文档标题
     */
    public String getTitle() {
        return title;
    }

    /**
     * 设置 API 文档标题。
     *
     * @param title API 文档标题
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * 获取 API 文档描述。
     *
     * @return API 文档描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置 API 文档描述。
     *
     * @param description API 文档描述
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 获取 API 文档版本号。
     *
     * @return API 文档版本号
     */
    public String getVersion() {
        return version;
    }

    /**
     * 设置 API 文档版本号。
     *
     * @param version API 文档版本号
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * 获取 API 文档联系人配置。
     *
     * @return API 文档联系人配置
     */
    public Contact getContact() {
        return contact;
    }

    /**
     * 设置 API 文档联系人配置。
     *
     * @param contact API 文档联系人配置，不能为 {@code null}
     */
    public void setContact(Contact contact) {
        this.contact = Objects.requireNonNull(contact, "联系人配置不能为空");
    }

    /**
     * 获取 API 文档安全方案配置。
     *
     * @return API 文档安全方案配置
     */
    public Security getSecurity() {
        return security;
    }

    /**
     * 设置 API 文档安全方案配置。
     *
     * @param security API 文档安全方案配置，不能为 {@code null}
     */
    public void setSecurity(Security security) {
        this.security = Objects.requireNonNull(security, "安全配置不能为空");
    }

    /**
     * API 文档联系人配置。
     */
    public static class Contact {

        /** 联系人姓名。 */
        private String name;

        /** 联系人邮箱。 */
        private String email;

        /** 联系人主页地址。 */
        private String url;

        /**
         * 获取联系人姓名。
         *
         * @return 联系人姓名
         */
        public String getName() {
            return name;
        }

        /**
         * 设置联系人姓名。
         *
         * @param name 联系人姓名
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * 获取联系人邮箱。
         *
         * @return 联系人邮箱
         */
        public String getEmail() {
            return email;
        }

        /**
         * 设置联系人邮箱。
         *
         * @param email 联系人邮箱
         */
        public void setEmail(String email) {
            this.email = email;
        }

        /**
         * 获取联系人主页地址。
         *
         * @return 联系人主页地址
         */
        public String getUrl() {
            return url;
        }

        /**
         * 设置联系人主页地址。
         *
         * @param url 联系人主页地址
         */
        public void setUrl(String url) {
            this.url = url;
        }
    }

    /**
     * API 文档安全方案配置。
     */
    public static class Security {

        /** 是否启用标准 HTTP Bearer JWT 安全方案。 */
        private boolean bearerToken = true;

        /** OpenAPI 安全方案名称。 */
        private String schemeName = "Bearer";

        /**
         * 判断是否启用标准 HTTP Bearer JWT 安全方案。
         *
         * @return {@code true} 表示启用，{@code false} 表示关闭
         */
        public boolean isBearerToken() {
            return bearerToken;
        }

        /**
         * 设置是否启用标准 HTTP Bearer JWT 安全方案。
         *
         * @param bearerToken {@code true} 表示启用，{@code false} 表示关闭
         */
        public void setBearerToken(boolean bearerToken) {
            this.bearerToken = bearerToken;
        }

        /**
         * 获取 OpenAPI 安全方案名称。
         *
         * @return OpenAPI 安全方案名称
         */
        public String getSchemeName() {
            return schemeName;
        }

        /**
         * 设置 OpenAPI 安全方案名称。
         *
         * @param schemeName OpenAPI 安全方案名称
         */
        public void setSchemeName(String schemeName) {
            this.schemeName = schemeName;
        }
    }
}

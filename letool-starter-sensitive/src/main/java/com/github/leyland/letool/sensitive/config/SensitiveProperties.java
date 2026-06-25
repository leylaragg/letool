package com.github.leyland.letool.sensitive.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.List;

/**
 * 脱敏模块配置属性 —— 对应 application.yml 中 {@code letool.sensitive} 前缀.
 *
 * <h3>示例配置</h3>
 * <pre>{@code
 * letool.sensitive:
 *   enabled: true
 *   jackson.enabled: true         # JSON 序列化时自动脱敏
 *   log.enabled: true             # 日志输出自动脱敏（%maskedMsg）
 *   dynamic:
 *     admin-roles: [ADMIN, DATA_ADMIN]   # 管理员角色列表——拥有这些角色的人看到原文
 *     header-name: X-Sensitive-Level     # 请求头控制脱敏级别: NONE / PARTIAL / FULL
 *   excludes:
 *     paths: [/admin/**, /api/internal/**]  # 不脱敏的 URL 路径（Ant 风格）
 *     fields: [*.createTime, *.id]           # 不脱敏的字段名（支持通配符）
 * }</pre>
 */
@ConfigurationProperties(prefix = "letool.sensitive")
public class SensitiveProperties {

    /** 总开关 —— false 则禁用整个脱敏模块 */
    private boolean enabled = true;
    /** Jackson 序列化脱敏配置 */
    private Jackson jackson = new Jackson();
    /** 日志脱敏配置 */
    private Log log = new Log();
    /** 动态脱敏配置（按用户角色决定脱敏级别） */
    private Dynamic dynamic = new Dynamic();
    /** 排除规则（指定哪些路径/字段不脱敏） */
    private Excludes excludes = new Excludes();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Jackson getJackson() { return jackson; }
    public void setJackson(Jackson jackson) { this.jackson = jackson; }
    public Log getLog() { return log; }
    public void setLog(Log log) { this.log = log; }
    public Dynamic getDynamic() { return dynamic; }
    public void setDynamic(Dynamic dynamic) { this.dynamic = dynamic; }
    public Excludes getExcludes() { return excludes; }
    public void setExcludes(Excludes excludes) { this.excludes = excludes; }

    /** Jackson 序列化脱敏 —— Controller 返回 JSON 时自动对 @Sensitive 字段脱敏 */
    public static class Jackson {
        /** 是否在 ObjectMapper 中注册 SensitiveModule */
        private boolean enabled = true;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    /** 日志脱敏 —— Logback/Log4j2 使用 %maskedMsg / %masked{m} 自动脱敏 */
    public static class Log {
        /** 是否启用日志脱敏转换器 */
        private boolean enabled = true;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    /** 动态脱敏 —— 根据用户角色或请求头动态决定脱敏级别 */
    public static class Dynamic {
        /** 是否启用动态脱敏 */
        private boolean enabled = true;
        /** 管理员角色列表 —— 拥有这些角色的用户看到原文 */
        private List<String> adminRoles = Collections.singletonList("ADMIN");
        /** 请求头名称 —— 值为 NONE（不脱敏）/ PARTIAL（部分脱敏）/ FULL（完全脱敏） */
        private String headerName = "X-Sensitive-Level";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public List<String> getAdminRoles() { return adminRoles; }
        public void setAdminRoles(List<String> adminRoles) { this.adminRoles = adminRoles; }
        public String getHeaderName() { return headerName; }
        public void setHeaderName(String headerName) { this.headerName = headerName; }
    }

    /** 排除规则 —— 指定哪些路径/字段跳过脱敏处理 */
    public static class Excludes {
        /** 不脱敏的 URL 路径列表，支持 Ant 风格通配符（如 /admin/**） */
        private List<String> paths = Collections.emptyList();
        /** 不脱敏的字段名列表，支持通配符（如 *.createTime 匹配所有类的 createTime 字段） */
        private List<String> fields = Collections.emptyList();

        public List<String> getPaths() { return paths; }
        public void setPaths(List<String> paths) { this.paths = paths; }
        public List<String> getFields() { return fields; }
        public void setFields(List<String> fields) { this.fields = fields; }
    }
}

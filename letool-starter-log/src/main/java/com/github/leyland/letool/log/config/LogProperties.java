package com.github.leyland.letool.log.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.List;

/**
 * 对应 {@code letool.log} 前缀的日志模块配置属性。
 *
 * <p>配置采用严格绑定，拼写错误或已删除的配置项会使应用启动失败，避免日志与审计
 * 行为在升级后被静默改变。</p>
 *
 * <h2>示例配置</h2>
 * <pre>{@code
 * letool.log:
 *   enabled: true
 *   trace:
 *     enabled: true
 *     header-name: X-Trace-Id
 *     generate-if-absent: true
 *   audit:
 *     enabled: true
 *   web-log:
 *     enabled: true
 *     include-body: false
 *     max-body-length: 1024
 *     exclude-paths: [/actuator/**, /swagger-ui/**]
 * }</pre>
 */
@ConfigurationProperties(prefix = "letool.log", ignoreUnknownFields = false)
public class LogProperties {

    /** 日志模块总开关。 */
    private boolean enabled = true;

    /** 链路追踪配置。 */
    private Trace trace = new Trace();

    /** 审计日志配置。 */
    private Audit audit = new Audit();

    /** Web 请求日志配置。 */
    private WebLog webLog = new WebLog();

    /**
     * 判断日志模块是否启用。
     *
     * @return {@code true} 表示启用日志模块
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置日志模块总开关。
     *
     * @param enabled 是否启用日志模块
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取链路追踪配置。
     *
     * @return 链路追踪配置
     */
    public Trace getTrace() {
        return trace;
    }

    /**
     * 设置链路追踪配置。
     *
     * @param trace 链路追踪配置
     */
    public void setTrace(Trace trace) {
        this.trace = trace;
    }

    /**
     * 获取审计日志配置。
     *
     * @return 审计日志配置
     */
    public Audit getAudit() {
        return audit;
    }

    /**
     * 设置审计日志配置。
     *
     * @param audit 审计日志配置
     */
    public void setAudit(Audit audit) {
        this.audit = audit;
    }

    /**
     * 获取 Web 请求日志配置。
     *
     * @return Web 请求日志配置
     */
    public WebLog getWebLog() {
        return webLog;
    }

    /**
     * 设置 Web 请求日志配置。
     *
     * @param webLog Web 请求日志配置
     */
    public void setWebLog(WebLog webLog) {
        this.webLog = webLog;
    }

    /**
     * TraceId 生成与 HTTP 传递配置。
     */
    public static class Trace {

        /** 链路追踪开关。 */
        private boolean enabled = true;

        /** 读取和回写 TraceId 的 HTTP 请求头名称。 */
        private String headerName = "X-Trace-Id";

        /** 请求未携带 TraceId 时是否自动生成。 */
        private boolean generateIfAbsent = true;

        /**
         * 判断链路追踪是否启用。
         *
         * @return {@code true} 表示启用链路追踪
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置链路追踪开关。
         *
         * @param enabled 是否启用链路追踪
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 获取 TraceId 请求头名称。
         *
         * @return TraceId 请求头名称
         */
        public String getHeaderName() {
            return headerName;
        }

        /**
         * 设置 TraceId 请求头名称。
         *
         * @param headerName TraceId 请求头名称
         */
        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }

        /**
         * 判断缺少 TraceId 时是否自动生成。
         *
         * @return {@code true} 表示自动生成 TraceId
         */
        public boolean isGenerateIfAbsent() {
            return generateIfAbsent;
        }

        /**
         * 设置缺少 TraceId 时的生成策略。
         *
         * @param generateIfAbsent 是否自动生成 TraceId
         */
        public void setGenerateIfAbsent(boolean generateIfAbsent) {
            this.generateIfAbsent = generateIfAbsent;
        }
    }

    /**
     * 关键业务操作审计配置。
     */
    public static class Audit {

        /** 审计日志开关。 */
        private boolean enabled = true;

        /**
         * 判断审计日志是否启用。
         *
         * @return {@code true} 表示启用审计日志
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置审计日志开关。
         *
         * @param enabled 是否启用审计日志
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * Spring MVC Controller 请求日志配置。
     */
    public static class WebLog {

        /** Web 请求日志开关。 */
        private boolean enabled = true;

        /** 是否记录请求 Header。 */
        private boolean includeHeaders;

        /** 是否记录请求体和响应体。 */
        private boolean includeBody;

        /** 请求体和响应体最大记录长度。 */
        private int maxBodyLength = 1024;

        /** 不记录日志的 URL 路径列表。 */
        private List<String> excludePaths = Collections.emptyList();

        /**
         * 判断 Web 请求日志是否启用。
         *
         * @return {@code true} 表示启用 Web 请求日志
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置 Web 请求日志开关。
         *
         * @param enabled 是否启用 Web 请求日志
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 判断是否记录请求 Header。
         *
         * @return {@code true} 表示记录请求 Header
         */
        public boolean isIncludeHeaders() {
            return includeHeaders;
        }

        /**
         * 设置是否记录请求 Header。
         *
         * @param includeHeaders 是否记录请求 Header
         */
        public void setIncludeHeaders(boolean includeHeaders) {
            this.includeHeaders = includeHeaders;
        }

        /**
         * 判断是否记录请求体和响应体。
         *
         * @return {@code true} 表示记录请求体和响应体
         */
        public boolean isIncludeBody() {
            return includeBody;
        }

        /**
         * 设置是否记录请求体和响应体。
         *
         * @param includeBody 是否记录请求体和响应体
         */
        public void setIncludeBody(boolean includeBody) {
            this.includeBody = includeBody;
        }

        /**
         * 获取请求体和响应体最大记录长度。
         *
         * @return 最大记录长度
         */
        public int getMaxBodyLength() {
            return maxBodyLength;
        }

        /**
         * 设置请求体和响应体最大记录长度。
         *
         * @param maxBodyLength 最大记录长度
         */
        public void setMaxBodyLength(int maxBodyLength) {
            this.maxBodyLength = maxBodyLength;
        }

        /**
         * 获取不记录日志的 URL 路径。
         *
         * @return 排除路径列表
         */
        public List<String> getExcludePaths() {
            return excludePaths;
        }

        /**
         * 设置不记录日志的 URL 路径。
         *
         * @param excludePaths 支持 Ant 风格表达式的排除路径列表
         */
        public void setExcludePaths(List<String> excludePaths) {
            this.excludePaths = excludePaths;
        }
    }
}

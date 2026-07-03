package com.github.leyland.letool.log.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.List;

/**
 * 日志模块配置属性 —— 对应 application.yml 中 {@code letool.log} 前缀.
 *
 * <h3>示例配置</h3>
 * <pre>{@code
 * letool.log:
 *   trace:
 *     enabled: true
 *     header-name: X-Trace-Id         # 请求头/响应头中 TraceId 的键名
 *     generate-if-absent: true        # 请求无 TraceId 时自动生成
 *   audit:
 *     enabled: true
 *     async: true                     # 异步写入（推荐开启，不阻塞业务）
 *     storage: file                   # 存储后端: memory / file / database
 *   web-log:
 *     enabled: true                   # 是否记录 Controller 层请求日志
 *     include-body: false             # 是否记录请求体（可能包含敏感数据）
 *     max-body-length: 1024           # 请求体截断长度（字节）
 *     exclude-paths: [/actuator/**, /swagger-ui/**]
 * }</pre>
 */
@ConfigurationProperties(prefix = "letool.log")
public class LogProperties {

    /** Whether the entire log starter is enabled. */
    private boolean enabled = true;

    /** 链路追踪配置 */
    private Trace trace = new Trace();
    /** 审计日志配置 */
    private Audit audit = new Audit();
    /** Web 请求日志配置 */
    private WebLog webLog = new WebLog();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Trace getTrace() { return trace; }
    public void setTrace(Trace trace) { this.trace = trace; }
    public Audit getAudit() { return audit; }
    public void setAudit(Audit audit) { this.audit = audit; }
    public WebLog getWebLog() { return webLog; }
    public void setWebLog(WebLog webLog) { this.webLog = webLog; }

    /** 链路追踪 —— TraceId 的生成和传递 */
    public static class Trace {
        /** 总开关 —— false 则禁用 TraceId 注入 */
        private boolean enabled = true;
        /** HTTP 请求头中读取/写入 TraceId 的键名，默认 X-Trace-Id */
        private String headerName = "X-Trace-Id";
        /** 请求头中没有 TraceId 时是否自动生成（网关上游可能已携带） */
        private boolean generateIfAbsent = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getHeaderName() { return headerName; }
        public void setHeaderName(String headerName) { this.headerName = headerName; }
        public boolean isGenerateIfAbsent() { return generateIfAbsent; }
        public void setGenerateIfAbsent(boolean generateIfAbsent) { this.generateIfAbsent = generateIfAbsent; }
    }

    /** 审计日志 —— 记录关键业务操作（如登录、删除用户、修改权限） */
    public static class Audit {
        /** 总开关 */
        private boolean enabled = true;
        /** 是否异步写入 —— true 不阻塞业务线程，false 同步写入保证可靠性 */
        private boolean async = true;
        /** 存储后端类型 —— memory（内存环形缓冲区）/ file（JSON Lines）/ database（自动检测表结构） */
        private String storage = "file";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isAsync() { return async; }
        public void setAsync(boolean async) { this.async = async; }
        public String getStorage() { return storage; }
        public void setStorage(String storage) { this.storage = storage; }
    }

    /** Web 请求日志 —— 自动记录所有 Controller 方法的请求路径、方法、耗时、状态码 */
    public static class WebLog {
        /** 总开关 */
        private boolean enabled = true;
        /** 是否记录请求 Header（可能包含 Authorization Token，敏感场景建议关闭） */
        private boolean includeHeaders = false;
        /** 是否记录请求体和响应体（生产环境建议关闭或设置较短 maxBodyLength） */
        private boolean includeBody = false;
        /** 请求体/响应体最大记录长度（字节），超长截断 */
        private int maxBodyLength = 1024;
        /** 不记录日志的 URL 路径列表，支持 Ant 风格通配符（如 /actuator/**） */
        private List<String> excludePaths = Collections.emptyList();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isIncludeHeaders() { return includeHeaders; }
        public void setIncludeHeaders(boolean includeHeaders) { this.includeHeaders = includeHeaders; }
        public boolean isIncludeBody() { return includeBody; }
        public void setIncludeBody(boolean includeBody) { this.includeBody = includeBody; }
        public int getMaxBodyLength() { return maxBodyLength; }
        public void setMaxBodyLength(int maxBodyLength) { this.maxBodyLength = maxBodyLength; }
        public List<String> getExcludePaths() { return excludePaths; }
        public void setExcludePaths(List<String> excludePaths) { this.excludePaths = excludePaths; }
    }
}

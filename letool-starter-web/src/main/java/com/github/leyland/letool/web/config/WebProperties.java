package com.github.leyland.letool.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.List;

/**
 * Web 模块配置属性 —— 对应 application.yml 中 {@code letool.web} 前缀.
 */
@ConfigurationProperties(prefix = "letool.web")
public class WebProperties {

    /** 总开关 */
    private boolean enabled = true;

    /** 响应包装配置 */
    private ResponseWrapper responseWrapper = new ResponseWrapper();

    /** XSS 过滤配置 */
    private XssFilter xssFilter = new XssFilter();

    /** SQL 注入防御配置 */
    private SqlInjectionFilter sqlInjectionFilter = new SqlInjectionFilter();

    /** 请求日志配置 */
    private RequestLog requestLog = new RequestLog();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public ResponseWrapper getResponseWrapper() { return responseWrapper; }
    public void setResponseWrapper(ResponseWrapper responseWrapper) { this.responseWrapper = responseWrapper; }
    public XssFilter getXssFilter() { return xssFilter; }
    public void setXssFilter(XssFilter xssFilter) { this.xssFilter = xssFilter; }
    public SqlInjectionFilter getSqlInjectionFilter() { return sqlInjectionFilter; }
    public void setSqlInjectionFilter(SqlInjectionFilter sqlInjectionFilter) { this.sqlInjectionFilter = sqlInjectionFilter; }
    public RequestLog getRequestLog() { return requestLog; }
    public void setRequestLog(RequestLog requestLog) { this.requestLog = requestLog; }

    /** 响应包装配置 */
    public static class ResponseWrapper {
        private boolean enabled = true;
        private List<String> excludePaths = Collections.emptyList();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public List<String> getExcludePaths() { return excludePaths; }
        public void setExcludePaths(List<String> excludePaths) { this.excludePaths = excludePaths; }
    }

    /** XSS 过滤配置 */
    public static class XssFilter {
        private boolean enabled = true;
        private List<String> excludePaths = Collections.emptyList();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public List<String> getExcludePaths() { return excludePaths; }
        public void setExcludePaths(List<String> excludePaths) { this.excludePaths = excludePaths; }
    }

    /** SQL 注入防御配置 */
    public static class SqlInjectionFilter {
        private boolean enabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    /** 请求日志配置 */
    public static class RequestLog {
        private boolean enabled = true;
        private boolean includeBody = false;
        private int maxBodySize = 4096;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isIncludeBody() { return includeBody; }
        public void setIncludeBody(boolean includeBody) { this.includeBody = includeBody; }
        public int getMaxBodySize() { return maxBodySize; }
        public void setMaxBodySize(int maxBodySize) { this.maxBodySize = maxBodySize; }
    }
}

package com.github.leyland.letool.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.util.List;
import java.util.Objects;

/**
 * Web 模块配置属性，对应 {@code letool.web} 配置前缀。
 *
 * <p>配置对象只描述模块真实提供的能力。集合属性始终保存不可变副本，避免配置绑定完成后
 * 被调用方继续修改而造成运行期行为漂移。</p>
 */
@ConfigurationProperties(prefix = "letool.web")
public class WebProperties {

    /** Web 模块总开关。 */
    private boolean enabled = true;

    /** 统一响应包装配置。 */
    private ResponseWrapper responseWrapper = new ResponseWrapper();

    /** API 版本路由配置。 */
    private ApiVersion apiVersion = new ApiVersion();

    /** 可重复读请求体配置。 */
    private RepeatableRequest repeatableRequest = new RepeatableRequest();

    /**
     * 判断 Web 模块是否启用。
     *
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置 Web 模块总开关。
     *
     * @param enabled 是否启用 Web 模块
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取统一响应包装配置。
     *
     * @return 非空响应包装配置
     */
    public ResponseWrapper getResponseWrapper() {
        return responseWrapper;
    }

    /**
     * 设置统一响应包装配置。
     *
     * @param responseWrapper 非空响应包装配置
     * @throws NullPointerException 当配置为 {@code null} 时抛出
     */
    public void setResponseWrapper(ResponseWrapper responseWrapper) {
        this.responseWrapper = Objects.requireNonNull(responseWrapper, "responseWrapper");
    }

    /**
     * 获取 API 版本路由配置。
     *
     * @return 非空 API 版本配置
     */
    public ApiVersion getApiVersion() {
        return apiVersion;
    }

    /**
     * 设置 API 版本路由配置。
     *
     * @param apiVersion 非空 API 版本配置
     * @throws NullPointerException 当配置为 {@code null} 时抛出
     */
    public void setApiVersion(ApiVersion apiVersion) {
        this.apiVersion = Objects.requireNonNull(apiVersion, "apiVersion");
    }

    /**
     * 获取可重复读请求体配置。
     *
     * @return 非空可重复读请求体配置
     */
    public RepeatableRequest getRepeatableRequest() {
        return repeatableRequest;
    }

    /**
     * 设置可重复读请求体配置。
     *
     * @param repeatableRequest 非空可重复读请求体配置
     * @throws NullPointerException 当配置为 {@code null} 时抛出
     */
    public void setRepeatableRequest(RepeatableRequest repeatableRequest) {
        this.repeatableRequest = Objects.requireNonNull(repeatableRequest, "repeatableRequest");
    }

    /**
     * 统一响应包装配置。
     */
    public static class ResponseWrapper {

        /** 是否启用统一响应包装。 */
        private boolean enabled = true;

        /** 不参与响应包装的应用内路径表达式。 */
        private List<String> excludePaths = List.of();

        /**
         * 判断统一响应包装是否启用。
         *
         * @return 启用时返回 {@code true}
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置统一响应包装开关。
         *
         * @param enabled 是否启用统一响应包装
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 获取响应包装排除路径。
         *
         * @return 不可变路径列表
         */
        public List<String> getExcludePaths() {
            return excludePaths;
        }

        /**
         * 设置响应包装排除路径。
         *
         * @param excludePaths 路径表达式；传 {@code null} 时按空列表处理
         */
        public void setExcludePaths(List<String> excludePaths) {
            this.excludePaths = immutableList(excludePaths);
        }
    }

    /**
     * API 主版本路由配置。
     */
    public static class ApiVersion {

        /** 是否启用 API 版本路由。 */
        private boolean enabled = true;

        /** 客户端传递 API 版本的请求头名称。 */
        private String headerName = "X-API-Version";

        /** 客户端传递 API 版本的查询参数名称。 */
        private String parameterName = "apiVersion";

        /**
         * 判断 API 版本路由是否启用。
         *
         * @return 启用时返回 {@code true}
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置 API 版本路由开关。
         *
         * @param enabled 是否启用 API 版本路由
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 获取 API 版本请求头名称。
         *
         * @return 请求头名称
         */
        public String getHeaderName() {
            return headerName;
        }

        /**
         * 设置 API 版本请求头名称。
         *
         * @param headerName 请求头名称
         */
        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }

        /**
         * 获取 API 版本查询参数名称。
         *
         * @return 查询参数名称
         */
        public String getParameterName() {
            return parameterName;
        }

        /**
         * 设置 API 版本查询参数名称。
         *
         * @param parameterName 查询参数名称
         */
        public void setParameterName(String parameterName) {
            this.parameterName = parameterName;
        }
    }

    /**
     * 可重复读请求体配置。
     */
    public static class RepeatableRequest {

        /** 是否启用请求体缓存，默认关闭以避免无条件内存开销。 */
        private boolean enabled;

        /** 单个请求允许缓存的最大请求体大小。 */
        private DataSize maxBodySize = DataSize.ofMegabytes(1);

        /** 不缓存请求体的应用内路径表达式。 */
        private List<String> excludePaths = List.of();

        /** 允许缓存的文本媒体类型表达式。 */
        private List<String> includeMediaTypes = List.of(
                "application/json",
                "application/*+json",
                "application/xml",
                "application/*+xml",
                "text/*");

        /**
         * 判断可重复读请求体是否启用。
         *
         * @return 启用时返回 {@code true}
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置可重复读请求体开关。
         *
         * @param enabled 是否启用请求体缓存
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 获取单个请求最大缓存大小。
         *
         * @return 非空数据大小
         */
        public DataSize getMaxBodySize() {
            return maxBodySize;
        }

        /**
         * 设置单个请求最大缓存大小。
         *
         * @param maxBodySize 非空数据大小
         * @throws NullPointerException 当配置为 {@code null} 时抛出
         */
        public void setMaxBodySize(DataSize maxBodySize) {
            this.maxBodySize = Objects.requireNonNull(maxBodySize, "maxBodySize");
        }

        /**
         * 获取请求体缓存排除路径。
         *
         * @return 不可变路径列表
         */
        public List<String> getExcludePaths() {
            return excludePaths;
        }

        /**
         * 设置请求体缓存排除路径。
         *
         * @param excludePaths 路径表达式；传 {@code null} 时按空列表处理
         */
        public void setExcludePaths(List<String> excludePaths) {
            this.excludePaths = immutableList(excludePaths);
        }

        /**
         * 获取允许缓存的媒体类型表达式。
         *
         * @return 不可变媒体类型列表
         */
        public List<String> getIncludeMediaTypes() {
            return includeMediaTypes;
        }

        /**
         * 设置允许缓存的媒体类型表达式。
         *
         * @param includeMediaTypes 媒体类型表达式；传 {@code null} 时按空列表处理
         */
        public void setIncludeMediaTypes(List<String> includeMediaTypes) {
            this.includeMediaTypes = immutableList(includeMediaTypes);
        }
    }

    /**
     * 将可空列表转换为不可变副本。
     *
     * @param values 原始列表
     * @param <T> 元素类型
     * @return 非空不可变列表
     */
    private static <T> List<T> immutableList(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}

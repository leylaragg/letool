package com.github.leyland.letool.data.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 数据库模块配置属性类，映射 {@code letool.data} 开头的配置项。
 *
 * <p>支持分页参数和对象映射行为的全局配置。YAML 配置示例：</p>
 * <pre>{@code
 * letool:
 *   data:
 *     enabled: true
 *     pagination:
 *       max-page-size: 1000
 *       default-page-size: 20
 *     mapping:
 *       auto-camel-case: true
 *       use-generated-keys: true
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
@ConfigurationProperties(prefix = "letool.data")
public class DataProperties {

    /** 是否启用数据库模块，默认 {@code true} */
    private boolean enabled = true;

    /** 分页配置 */
    private Pagination pagination = new Pagination();

    /** 对象映射配置 */
    private Mapping mapping = new Mapping();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Pagination getPagination() { return pagination; }
    public void setPagination(Pagination pagination) { this.pagination = pagination; }
    public Mapping getMapping() { return mapping; }
    public void setMapping(Mapping mapping) { this.mapping = mapping; }

    /**
     * 分页查询配置。
     *
     * <p>控制分页查询的默认行为和安全限制。</p>
     */
    public static class Pagination {

        /** 单次分页最大条数，防止恶意查询拖垮数据库，默认 1000 */
        private int maxPageSize = 1000;

        /** 分页默认每页条数，当调用方未指定 pageSize 时使用，默认 20 */
        private int defaultPageSize = 20;

        public int getMaxPageSize() { return maxPageSize; }
        public void setMaxPageSize(int maxPageSize) { this.maxPageSize = maxPageSize; }
        public int getDefaultPageSize() { return defaultPageSize; }
        public void setDefaultPageSize(int defaultPageSize) { this.defaultPageSize = defaultPageSize; }
    }

    /**
     * 对象关系映射配置。
     *
     * <p>控制 Java 字段与数据库列的自动映射行为。</p>
     */
    public static class Mapping {

        /** 是否自动将下划线列名转换为驼峰字段名，默认 {@code true} */
        private boolean autoCamelCase = true;

        /** INSERT 时是否使用 JDBC 自动生成主键（自增主键回填），默认 {@code true} */
        private boolean useGeneratedKeys = true;

        public boolean isAutoCamelCase() { return autoCamelCase; }
        public void setAutoCamelCase(boolean autoCamelCase) { this.autoCamelCase = autoCamelCase; }
        public boolean isUseGeneratedKeys() { return useGeneratedKeys; }
        public void setUseGeneratedKeys(boolean useGeneratedKeys) { this.useGeneratedKeys = useGeneratedKeys; }
    }
}

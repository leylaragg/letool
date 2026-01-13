package com.github.leyland.letool.data.mapper.context;

import java.util.TimeZone;

/**
 * 映射配置类
 * 管理映射的配置参数
 *
 * @author leyland
 * @date 2025-01-12
 */
public class MappingConfig {

    /**
     * 默认日期格式
     */
    private String defaultDateFormat = "yyyy-MM-dd HH:mm:ss";

    /**
     * 默认时区
     */
    private TimeZone defaultTimeZone = TimeZone.getTimeZone("Asia/Shanghai");

    /**
     * 是否忽略大小写
     */
    private boolean ignoreCase = false;

    /**
     * 是否忽略空值
     */
    private boolean ignoreNull = false;

    /**
     * 是否启用处理器链
     */
    private boolean enableHandlerChain = true;

    /**
     * 是否使用缓存
     */
    private boolean enableCache = true;

    public MappingConfig() {
    }

    public MappingConfig(String defaultDateFormat, TimeZone defaultTimeZone) {
        this.defaultDateFormat = defaultDateFormat;
        this.defaultTimeZone = defaultTimeZone;
    }

    public String getDefaultDateFormat() {
        return defaultDateFormat;
    }

    public MappingConfig setDefaultDateFormat(String defaultDateFormat) {
        this.defaultDateFormat = defaultDateFormat;
        return this;
    }

    public TimeZone getDefaultTimeZone() {
        return defaultTimeZone;
    }

    public MappingConfig setDefaultTimeZone(TimeZone defaultTimeZone) {
        this.defaultTimeZone = defaultTimeZone;
        return this;
    }

    public MappingConfig setDefaultTimeZone(String timezone) {
        this.defaultTimeZone = TimeZone.getTimeZone(timezone);
        return this;
    }

    public boolean isIgnoreCase() {
        return ignoreCase;
    }

    public MappingConfig setIgnoreCase(boolean ignoreCase) {
        this.ignoreCase = ignoreCase;
        return this;
    }

    public boolean isIgnoreNull() {
        return ignoreNull;
    }

    public MappingConfig setIgnoreNull(boolean ignoreNull) {
        this.ignoreNull = ignoreNull;
        return this;
    }

    public boolean isEnableHandlerChain() {
        return enableHandlerChain;
    }

    public MappingConfig setEnableHandlerChain(boolean enableHandlerChain) {
        this.enableHandlerChain = enableHandlerChain;
        return this;
    }

    public boolean isEnableCache() {
        return enableCache;
    }

    public MappingConfig setEnableCache(boolean enableCache) {
        this.enableCache = enableCache;
        return this;
    }

    /**
     * 创建默认配置
     */
    public static MappingConfig defaultConfig() {
        return new MappingConfig();
    }

    /**
     * 创建副本
     */
    public MappingConfig copy() {
        MappingConfig copy = new MappingConfig();
        copy.defaultDateFormat = this.defaultDateFormat;
        copy.defaultTimeZone = this.defaultTimeZone;
        copy.ignoreCase = this.ignoreCase;
        copy.ignoreNull = this.ignoreNull;
        copy.enableHandlerChain = this.enableHandlerChain;
        copy.enableCache = this.enableCache;
        return copy;
    }
}

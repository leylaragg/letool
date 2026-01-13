package com.github.leyland.letool.data.mapper.context;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * 映射上下文
 * 保存映射过程中的上下文信息
 *
 * @author leyland
 * @date 2025-01-12
 */
public class MappingContext {

    /**
     * 源对象数组
     */
    private final Object[] sources;

    /**
     * 目标对象
     */
    private final Object target;

    /**
     * 映射配置
     */
    private final MappingConfig config;

    /**
     * 上下文属性（用于存储临时数据）
     */
    private final Map<String, Object> attributes;

    /**
     * 当前正在处理的字段（用于调试和日志）
     */
    private Field currentField;

    /**
     * 错误信息（可选）
     */
    private Throwable error;

    public MappingContext(Object[] sources, Object target, MappingConfig config) {
        this.sources = sources;
        this.target = target;
        this.config = config != null ? config : MappingConfig.defaultConfig();
        this.attributes = new HashMap<>();
    }

    /**
     * 获取源对象
     *
     * @param index 索引
     * @return 源对象
     */
    public Object getSource(int index) {
        if (index < 0 || index >= sources.length) {
            return null;
        }
        return sources[index];
    }

    /**
     * 获取所有源对象
     *
     * @return 源对象数组
     */
    public Object[] getSources() {
        return sources;
    }

    /**
     * 获取目标对象
     *
     * @return 目标对象
     */
    public Object getTarget() {
        return target;
    }

    /**
     * 获取映射配置
     *
     * @return 映射配置
     */
    public MappingConfig getConfig() {
        return config;
    }

    /**
     * 设置上下文属性
     *
     * @param key 键
     * @param value 值
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * 获取上下文属性
     *
     * @param key 键
     * @return 值
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    /**
     * 获取上下文属性（带默认值）
     *
     * @param key 键
     * @param defaultValue 默认值
     * @return 值
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, T defaultValue) {
        Object value = attributes.get(key);
        return value != null ? (T) value : defaultValue;
    }

    /**
     * 获取当前字段
     *
     * @return 当前字段
     */
    public Field getCurrentField() {
        return currentField;
    }

    /**
     * 设置当前字段
     *
     * @param currentField 当前字段
     */
    public void setCurrentField(Field currentField) {
        this.currentField = currentField;
    }

    /**
     * 获取错误信息
     *
     * @return 错误信息
     */
    public Throwable getError() {
        return error;
    }

    /**
     * 设置错误信息
     *
     * @param error 错误信息
     */
    public void setError(Throwable error) {
        this.error = error;
    }

    /**
     * 清空属性
     */
    public void clearAttributes() {
        attributes.clear();
    }

    /**
     * 创建子上下文
     */
    public MappingContext createChildContext(Object[] sources, Object target) {
        MappingContext child = new MappingContext(sources, target, config);
        child.setAttributes(this.attributes);
        return child;
    }

    /**
     * 设置属性
     */
    private void setAttributes(Map<String, Object> attributes) {
        this.attributes.putAll(attributes);
    }
}

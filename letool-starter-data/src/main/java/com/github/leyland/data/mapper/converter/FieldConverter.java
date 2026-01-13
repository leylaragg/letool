package com.github.leyland.data.mapper.converter;

/**
 * 字段转换器接口
 * 定义字段值的类型转换逻辑
 *
 * @author leyland
 * @date 2025-01-12
 */
public interface FieldConverter<S, T> {

    /**
     * 转换值
     *
     * @param sourceValue 源值
     * @param targetType 目标类型
     * @return 转换后的值
     */
    T convert(S sourceValue, Class<T> targetType);

    /**
     * 判断是否支持该转换
     *
     * @param sourceType 源类型
     * @param targetType 目标类型
     * @return 是否支持
     */
    default boolean supports(Class<?> sourceType, Class<?> targetType) {
        return true;
    }

    /**
     * 获取转换器优先级
     * 值越小优先级越高
     *
     * @return 优先级
     */
    default int getPriority() {
        return 0;
    }
}

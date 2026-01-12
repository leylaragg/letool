package com.github.leyland.data.mapper;

/**
 * 类型转换器接口
 * 用于自定义字段类型转换逻辑
 *
 * @author leyland
 * @date 2025-01-08
 */
public interface TypeConverter<S, T> {

    /**
     * 将源值转换为目标类型
     *
     * @param sourceValue 源值
     * @param targetType  目标类型
     * @return 转换后的值
     */
    T convert(S sourceValue, Class<T> targetType);

    /**
     * 默认的转换器实现
     */
    class DefaultConverter implements TypeConverter<Object, Object> {

        @Override
        public Object convert(Object sourceValue, Class<Object> targetType) {
            return TypeConverterUtil.convert(sourceValue, targetType);
        }
    }
}

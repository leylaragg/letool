package com.github.leyland.letool.tool.mapper;

/**
 * 映射监听器接口
 * 用于在映射过程中执行自定义逻辑
 *
 * @author leyland
 * @date 2025-01-08
 */
public interface MapperListener {

    /**
     * 映射开始前调用
     *
     * @param target 目标对象
     * @param sources 源对象数组
     */
    default void beforeMapping(Object target, Object[] sources) {
    }

    /**
     * 映射完成后调用
     *
     * @param target 目标对象
     * @param sources 源对象数组
     */
    default void afterMapping(Object target, Object[] sources) {
    }

    /**
     * 字段映射失败时调用
     *
     * @param target 目标对象
     * @param targetField 目标字段
     * @param source 源对象
     * @param exception 异常
     */
    default void onFieldMappingError(Object target, java.lang.reflect.Field targetField, Object source, Exception exception) {
    }
}

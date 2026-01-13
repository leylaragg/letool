package com.github.leyland.letool.data.mapper.handler;

import com.github.leyland.letool.data.mapper.context.MappingContext;

import java.lang.reflect.Field;

/**
 * 字段映射处理器接口
 * 定义字段映射的处理逻辑
 *
 * @author leyland
 * @date 2025-01-12
 */
public interface FieldMappingHandler {

    /**
     * 处理字段映射
     *
     * @param context 映射上下文
     * @param targetField 目标字段
     * @param sourceValue 源值
     * @return 处理后的值
     */
    Object handle(MappingContext context, Field targetField, Object sourceValue);

    /**
     * 判断是否支持处理该字段
     *
     * @param context 映射上下文
     * @param targetField 目标字段
     * @return 是否支持
     */
    boolean supports(MappingContext context, Field targetField);

    /**
     * 获取处理优先级
     * 值越小优先级越高
     *
     * @return 优先级
     */
    int getPriority();

    /**
     * 获取处理器名称
     *
     * @return 处理器名称
     */
    String getName();
}

package com.github.leyland.data.mapper.annotation;

import com.github.leyland.data.mapper.converter.FieldConverter;

import java.lang.annotation.*;

/**
 * 字段映射注解
 * 标记需要进行映射的字段
 *
 * @author leyland
 * @date 2025-01-12
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MapField {

    /**
     * 源对象索引（当有多个源对象时）
     * 默认为 0，表示第一个源对象
     */
    int sourceIndex() default 0;

    /**
     * 源字段路径
     * 支持嵌套访问，如：user.address.city
     * 为空时使用目标字段名
     */
    String sourcePath() default "";

    /**
     * 字段转换器
     * 用于自定义字段值的转换逻辑
     */
    Class<? extends FieldConverter> converter() default FieldConverter.class;

    /**
     * 格式化模式
     * 用于日期、数字等类型的格式化
     */
    String format() default "";

    /**
     * 是否忽略空值
     * true：源字段为 null 时不覆盖目标字段
     */
    boolean ignoreNull() default false;

    /**
     * 映射优先级
     * 值越小优先级越高
     */
    int priority() default 0;

    /**
     * 默认值
     * 当源值为 null 时使用此默认值
     */
    String defaultValue() default "";
}

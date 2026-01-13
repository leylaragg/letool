package com.github.leyland.letool.data.mapper.annotation;

import java.lang.annotation.*;

/**
 * 忽略字段注解
 * 标记不需要映射的字段
 *
 * @author leyland
 * @date 2025-01-12
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MapIgnore {

    /**
     * 忽略条件
     * 使用 SpEL 表达式定义条件
     * 为空时表示总是忽略
     */
    String condition() default "";

    /**
     * 忽略原因
     * 用于文档说明
     */
    String reason() default "";
}

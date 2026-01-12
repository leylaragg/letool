package com.github.leyland.data.mapper;

import java.lang.annotation.*;

/**
 * 数据脱敏注解
 * 用于标记需要脱敏的字段
 *
 * @author leyland
 * @date 2025-01-08
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Sensitive {

    /**
     * 脱敏策略
     */
    SensitiveType value() default SensitiveType.DEFAULT;

    /**
     * 自定义脱敏格式
     * 使用占位符：
     * - {start}: 保留开头字符数
     * - {end}: 保留结尾字符数
     * - {mask}: 脱敏字符
     * - {value}: 原始值
     *
     * 示例：
     * - 手机号："{start}****{end}" (保留前3后4)
     * - 身份证："{start}************{end}" (保留前1后4)
     * - 邮箱："{start}***{end}" (保留前1和@之后)
     */
    String pattern() default "";

    /**
     * 脱敏字符
     * 默认为 *
     */
    char maskChar() default '*';
}

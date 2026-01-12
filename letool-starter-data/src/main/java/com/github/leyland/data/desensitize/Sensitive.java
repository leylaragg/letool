package com.github.leyland.data.desensitize;

import java.lang.annotation.*;

/**
 * 数据脱敏注解
 * 用于标记需要脱敏的字段
 *
 * @author leyland
 * @date 2025-01-12
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Sensitive {

    /**
     * 脱敏类型
     */
    SensitiveType value() default SensitiveType.DEFAULT;

    /**
     * 自定义滑块脱敏参数
     * 当 type = CUSTOM_SLIDE 时有效
     */
    int leftKeep() default 0;

    /**
     * 自定义滑块脱敏参数
     * 当 type = CUSTOM_SLIDE 时有效
     */
    int rightKeep() default 0;

    /**
     * 脱敏字符
     * 默认为 *
     */
    String maskString() default "*";

    /**
     * 是否反转
     * 只对滑块脱敏和索引脱敏有效
     */
    boolean reverse() default false;

    /**
     * 自定义正则表达式
     * 当 type = CUSTOM_REGEX 时有效
     */
    String regex() default "";

    /**
     * 自定义替换内容
     * 当 type = CUSTOM_REGEX 时有效
     */
    String replacement() default "";

    /**
     * 自定义索引规则
     * 当 type = CUSTOM_INDEX 时有效
     * 支持多个规则，如 {"1", "3-5", "9-"}
     */
    String[] indexRules() default {};
}

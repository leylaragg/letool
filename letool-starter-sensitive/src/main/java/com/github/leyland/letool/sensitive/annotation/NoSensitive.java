package com.github.leyland.letool.sensitive.annotation;

import java.lang.annotation.*;

/**
 * 排除脱敏注解 —— 标记此注解的字段不参与任何脱敏处理.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE})
public @interface NoSensitive {
}

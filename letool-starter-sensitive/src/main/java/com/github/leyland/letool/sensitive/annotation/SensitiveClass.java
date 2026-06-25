package com.github.leyland.letool.sensitive.annotation;

import java.lang.annotation.*;

/**
 * 类级脱敏注解 —— 标记类中所有 String 字段默认参与脱敏（被 @NoSensitive 标记的字段除外）.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SensitiveClass {
}

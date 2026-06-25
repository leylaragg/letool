package com.github.leyland.letool.security.jwt.annotation;

import java.lang.annotation.*;

/**
 * 跳过认证注解
 * <p>
 * 标注在 Controller 类或方法上，表示跳过认证检查 (用于白名单路径)
 *
 * @author Rungo
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SkipAuth {
}
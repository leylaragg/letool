package com.github.leyland.letool.security.jwt.annotation;

import java.lang.annotation.*;

/**
 * 需要登录认证注解
 * <p>
 * 标注在 Controller 类或方法上，表示需要用户登录才能访问
 *
 * @author Rungo
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireLogin {
}
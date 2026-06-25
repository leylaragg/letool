package com.github.leyland.letool.security.jwt.annotation;

import java.lang.annotation.*;

/**
 * 需要权限注解
 * <p>
 * 标注在 Controller 类或方法上，表示需要特定权限才能访问
 *
 * @author Rungo
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {

    /**
     * 需要的权限列表 (只需满足其中一个)
     */
    String[] value();

    /**
     * 是否需要满足所有权限 (默认只需满足其中一个)
     */
    boolean requireAll() default false;
}
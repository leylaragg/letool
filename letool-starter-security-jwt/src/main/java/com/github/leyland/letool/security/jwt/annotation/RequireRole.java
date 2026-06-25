package com.github.leyland.letool.security.jwt.annotation;

import java.lang.annotation.*;

/**
 * 需要角色注解
 * <p>
 * 标注在 Controller 类或方法上，表示需要特定角色才能访问
 *
 * @author Rungo
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {

    /**
     * 需要的角色列表 (只需满足其中一个)
     */
    String[] value();

    /**
     * 是否需要满足所有角色 (默认只需满足其中一个)
     */
    boolean requireAll() default false;
}
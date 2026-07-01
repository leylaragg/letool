package com.github.leyland.letool.security.annotation;

import java.lang.annotation.*;

/**
 * 角色要求注解，标注在类或方法上表示当前用户必须拥有指定角色之一。
 *
 * <p>不满足时抛出 {@link org.springframework.security.access.AccessDeniedException}，
 * 由 {@link com.github.leyland.letool.security.handler.AccessDeniedExceptionHandler} 返回 403。</p>
 *
 * <pre>{@code
 * @RequireRole("ADMIN")
 * @DeleteMapping("/user/{id}")
 * public R<Void> deleteUser(@PathVariable Long id) { ... }
 *
 * @RequireRole({"ADMIN", "SUPER_ADMIN"})  // 满足任一即可
 * @GetMapping("/admin/dashboard")
 * public R<Dashboard> dashboard() { ... }
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {

    /** 需要的角色标识列表，满足任一即可 */
    String[] value();
}

package com.github.leyland.letool.security.annotation;

import java.lang.annotation.*;

/**
 * 权限要求注解，标注在类或方法上表示当前用户必须拥有指定权限之一。
 *
 * <p>不满足时抛出 {@link org.springframework.security.access.AccessDeniedException}。</p>
 *
 * <pre>{@code
 * @RequirePermission("user:write")
 * @PostMapping("/user")
 * public R<User> createUser(@RequestBody User user) { ... }
 *
 * @RequirePermission({"order:export", "order:admin"})  // 满足任一即可
 * @GetMapping("/order/export")
 * public void export(HttpServletResponse response) { ... }
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {

    /** 需要的权限标识列表，满足任一即可 */
    String[] value();
}

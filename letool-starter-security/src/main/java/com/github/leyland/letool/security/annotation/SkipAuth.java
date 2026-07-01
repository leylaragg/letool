package com.github.leyland.letool.security.annotation;

import java.lang.annotation.*;

/**
 * 跳过认证注解，标注在类或方法上表示该接口无需登录即可访问。
 *
 * <p>类级注解对该类所有方法生效。</p>
 *
 * <pre>{@code
 * @SkipAuth
 * @GetMapping("/public/health")
 * public R<String> health() { return R.ok("UP"); }
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SkipAuth {
}

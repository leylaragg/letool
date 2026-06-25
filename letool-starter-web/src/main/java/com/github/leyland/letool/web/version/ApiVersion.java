package com.github.leyland.letool.web.version;

import java.lang.annotation.*;

/**
 * API 版本注解 —— 标注在 Controller 方法上，支持同一路径不同版本的接口共存.
 *
 * <pre>{@code
 * @ApiVersion(1)
 * @GetMapping("/user/{id}")
 * public R<User> getUserV1(@PathVariable Long id) { ... }
 *
 * @ApiVersion(2)
 * @GetMapping("/user/{id}")
 * public R<UserV2> getUserV2(@PathVariable Long id) { ... }
 * }</pre>
 *
 * <p>客户端通过请求头 {@code X-API-Version} 或请求参数 {@code apiVersion} 指定版本.</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiVersion {

    /** API 版本号 */
    int value();
}

package com.github.leyland.letool.web.version;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明 Controller 类或方法对应的 API 主版本。
 *
 * <p>客户端默认通过请求头 {@code X-API-Version} 或查询参数 {@code apiVersion} 指定版本，
 * 名称可通过 {@code letool.web.api-version} 调整。方法声明覆盖类声明，版本值必须为正整数。</p>
 *
 * <pre>{@code
 * @ApiVersion(1)
 * @GetMapping("/users/{id}")
 * public User getUserV1(@PathVariable Long id) {
 *     return userService.getV1(id);
 * }
 * }</pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiVersion {

    /**
     * 获取接口主版本。
     *
     * @return 大于 0 的主版本号
     */
    int value();
}

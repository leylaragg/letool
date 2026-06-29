package com.github.leyland.letool.swagger.annotation;

import java.lang.annotation.*;

/**
 * API 分组注解，用于标记 Controller 类所属的 API 文档分组。
 *
 * <p>将此注解放在 Spring MVC 的 {@code @RestController} 或 {@code @Controller}
 * 类上，配合 {@link com.github.leyland.letool.swagger.config.SwaggerAutoConfiguration}
 * 自动配置，可以将该类自动归入指定的 API 文档分组中，在 Knife4j / Swagger UI 界面中以
 * 分组维度展示接口文档。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * @RestController
 * @ApiGroup(value = "用户管理", description = "用户相关的增删改查接口")
 * @RequestMapping("/api/users")
 * public class UserController {
 *     // ...
 * }
 * }</pre>
 *
 * <p>分组信息最终会被 {@link com.github.leyland.letool.swagger.config.SwaggerAutoConfiguration}
 * 中的 {@code GroupedOpenApi} Bean 所使用，自动生成对应的文档分组。</p>
 *
 * @author leyland
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiGroup {

    // ======================== 注解属性 ========================

    /**
     * API 分组的名称，作为 Knife4j/Swagger UI 中展示的分组标识。
     *
     * @return 分组名称（必填）
     */
    String value();

    /**
     * API 分组的描述信息，用于在文档界面中补充说明该分组的用途。
     *
     * @return 分组描述（可选，默认为空字符串）
     */
    String description() default "";
}

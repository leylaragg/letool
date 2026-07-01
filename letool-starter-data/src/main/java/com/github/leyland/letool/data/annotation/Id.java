package com.github.leyland.letool.data.annotation;

import java.lang.annotation.*;

/**
 * 主键标记注解，用于标识实体类中的主键字段。
 *
 * <p>标记此注解的字段将被框架用于：
 * <ul>
 *   <li>{@code selectById} — 根据主键查询单条记录</li>
 *   <li>{@code deleteById} — 根据主键删除记录</li>
 *   <li>{@code update} — 根据主键确定要更新的行</li>
 *   <li>{@code insert} — 判断是否使用自增主键回填</li>
 * </ul>
 * </p>
 *
 * <p>每个实体类应当有且仅有一个字段标记此注解。如果未标记任何字段，
 * 框架默认查找名为 {@code id} 的字段作为主键。</p>
 *
 * <pre>{@code
 * public class User {
 *     @Id
 *     private Long id;
 *     ...
 * }
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Id {
}

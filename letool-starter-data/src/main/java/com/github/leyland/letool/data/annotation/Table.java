package com.github.leyland.letool.data.annotation;

import java.lang.annotation.*;

/**
 * 数据库表名映射注解，用于指定实体类对应的数据库表名。
 *
 * <p>当实体类名与数据库表名不一致时，使用此注解显式指定映射关系。
 * 如果未使用此注解，框架将自动按驼峰转下划线规则推导表名
 * （例如 {@code UserOrder} → {@code user_order}）。</p>
 *
 * <pre>{@code
 * @Table("sys_user")
 * public class User {
 *     ...
 * }
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Table {

    /**
     * 数据库表名。
     *
     * @return 数据库表名
     */
    String value();
}

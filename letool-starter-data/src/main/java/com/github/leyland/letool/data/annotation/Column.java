package com.github.leyland.letool.data.annotation;

import java.lang.annotation.*;

/**
 * 数据库列名映射注解，用于指定 Java 字段对应的数据库列名。
 *
 * <p>当实体类的字段名与数据库列名不一致时，使用此注解显式指定映射关系。
 * 如果未使用此注解，框架将自动按驼峰转下划线规则推导列名。</p>
 *
 * <pre>{@code
 * @Column("user_name")
 * private String userName;
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Column {

    /**
     * 数据库列名。
     *
     * @return 数据库列名
     */
    String value();
}

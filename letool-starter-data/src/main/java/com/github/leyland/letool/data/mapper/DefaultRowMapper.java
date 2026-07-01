package com.github.leyland.letool.data.mapper;

/**
 * 默认行映射器，继承 {@link BeanPropertyRowMapper} 并默认启用自动驼峰转换。
 *
 * <p>使用此映射器时，数据库列名 {@code user_name} 将自动映射到 Java 字段 {@code userName}。
 * 如果实体类已使用 {@link com.github.leyland.letool.data.annotation.Column @Column}
 * 注解显式指定列名，则优先使用注解指定的名称。</p>
 *
 * <pre>{@code
 * // 用法
 * List<User> users = jdbcTemplate.query(sql, new DefaultRowMapper<>(User.class), params);
 * }</pre>
 *
 * @param <T> 实体类型
 * @author leyland
 * @since 2.0.0
 */
public class DefaultRowMapper<T> extends BeanPropertyRowMapper<T> {

    /**
     * 构造默认行映射器，自动启用驼峰转换。
     *
     * @param mappedClass 目标实体类
     */
    public DefaultRowMapper(Class<T> mappedClass) {
        super(mappedClass, true);
    }
}

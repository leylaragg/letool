package com.github.leyland.letool.data.annotation;

import java.lang.annotation.*;

/**
 * 瞬态字段标记注解，用于排除实体类中不需要映射到数据库的字段。
 *
 * <p>标记此注解的字段将在以下操作中被忽略：
 * <ul>
 *   <li>INSERT — 不写入数据库</li>
 *   <li>UPDATE — 不更新数据库</li>
 *   <li>RowMapper — 不自动映射结果集</li>
 * </ul>
 * </p>
 *
 * <p>适用场景：存放计算结果、关联数据、临时状态等非数据库字段。</p>
 *
 * <pre>{@code
 * public class User {
 *     private Long id;
 *     private String name;
 *
 *     @Transient
 *     private List<Role> roles;  // 关联查询结果，不映射到数据库
 * }
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Transient {
}

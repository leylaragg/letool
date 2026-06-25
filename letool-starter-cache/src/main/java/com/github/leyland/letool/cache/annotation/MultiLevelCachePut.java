package com.github.leyland.letool.cache.annotation;

import java.lang.annotation.*;

/**
 * 缓存更新注解 —— 标注在方法上，执行方法后用返回值更新缓存.
 *
 * <pre>{@code
 * @MultiLevelCachePut(name = "userCache", key = "#user.id")
 * public User updateUser(User user) {
 *     userMapper.updateById(user);
 *     return user;
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MultiLevelCachePut {

    /** 缓存实例名称 */
    String name();

    /** 缓存键（支持 SpEL 表达式） */
    String key();

    /** 过期时间（秒），0 表示使用缓存实例的默认 TTL */
    long ttl() default 0;
}

package com.github.leyland.letool.cache.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 缓存读取注解 —— 标注在方法上，自动从缓存读取，未命中时执行方法并回填缓存.
 *
 * <pre>{@code
 * @MultiLevelCacheable(name = "userCache", key = "#userId", ttl = 3600)
 * public User getUser(Long userId) {
 *     return userMapper.selectById(userId);
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MultiLevelCacheable {

    /** 缓存实例名称（必须先通过 {@code CacheManager.getOrCreate()} 注册） */
    String name();

    /** 缓存键（支持 SpEL 表达式，如 "#userId" 或 "#user.id"） */
    String key();

    /** 过期时间（秒），0 表示使用缓存实例的默认 TTL */
    long ttl() default 0;
}

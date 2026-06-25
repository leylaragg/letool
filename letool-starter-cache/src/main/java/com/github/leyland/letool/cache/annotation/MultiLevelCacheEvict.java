package com.github.leyland.letool.cache.annotation;

import java.lang.annotation.*;

/**
 * 缓存清除注解 —— 标注在方法上，执行方法后删除缓存.
 *
 * <pre>{@code
 * @MultiLevelCacheEvict(name = "userCache", key = "#userId")
 * public void deleteUser(Long userId) {
 *     userMapper.deleteById(userId);
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MultiLevelCacheEvict {

    /** 缓存实例名称 */
    String name();

    /** 缓存键（支持 SpEL 表达式） */
    String key();
}

package com.github.leyland.letool.cache.consistency;

import java.util.Objects;

/**
 * 一次数据库修改对应的缓存身份与一致性模式。
 *
 * @param mode 一致性模式
 * @param cacheName 缓存区域名称
 * @param serializedKey 已序列化业务 Key
 */
public record CacheMutation(
        CacheConsistencyMode mode,
        String cacheName,
        String serializedKey) {

    public CacheMutation {
        Objects.requireNonNull(mode, "一致性模式不能为空");
    }

    /** 创建不需要持久围栏的事务型修改。 */
    public static CacheMutation transactional(CacheConsistencyMode mode) {
        return new CacheMutation(mode, null, null);
    }
}

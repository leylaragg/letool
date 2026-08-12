package com.github.leyland.letool.cache.consistency;

/**
 * 数据库修改与缓存失效之间的一致性模式。
 */
public enum CacheConsistencyMode {

    /**
     * 在数据库事务提交后执行缓存失效，不持久化待处理事件。
     */
    TRANSACTIONAL,

    /**
     * 使用写入围栏和持久化事件覆盖应用宕机后的恢复窗口。
     */
    DURABLE
}

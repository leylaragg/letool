package com.github.leyland.letool.cache.consistency;

/**
 * 数据库修改成功后的缓存处理策略。
 */
public enum CacheWritePolicy {

    /**
     * 删除旧缓存，由下一次读取从数据库重建。
     */
    INVALIDATE,

    /**
     * 在事务提交后把业务方法返回值写入缓存。
     */
    UPDATE
}

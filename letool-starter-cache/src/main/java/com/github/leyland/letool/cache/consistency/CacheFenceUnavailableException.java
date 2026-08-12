package com.github.leyland.letool.cache.consistency;

/**
 * 无法在数据库修改前安全建立写入围栏时抛出的异常。
 */
public class CacheFenceUnavailableException extends RuntimeException {

    /**
     * 创建不包含业务 Key 的安全异常。
     */
    public CacheFenceUnavailableException() {
        super("缓存写入围栏不可用");
    }

    /**
     * 创建保留底层原因但不暴露业务 Key 的安全异常。
     *
     * @param cause Redis 操作失败原因
     */
    public CacheFenceUnavailableException(Throwable cause) {
        super("缓存写入围栏不可用", cause);
    }
}

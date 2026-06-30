package com.github.leyland.letool.datastructure.exception;

/**
 * 数据结构操作异常 —— 用于树构建、决策链执行、链表操作等场景中的异常.
 *
 * @author leyland
 * @since 2.0.0
 */
public class DataStructureException extends RuntimeException {

    public DataStructureException(String message) {
        super(message);
    }

    public DataStructureException(String message, Throwable cause) {
        super(message, cause);
    }
}

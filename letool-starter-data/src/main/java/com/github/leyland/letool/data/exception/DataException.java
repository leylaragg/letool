package com.github.leyland.letool.data.exception;

import com.github.leyland.letool.tool.exception.LetoolException;

/**
 * 数据库操作异常，封装数据访问层发生的所有异常。
 *
 * <p>继承自 {@link LetoolException}，支持错误码 + 国际化消息。
 * 常见使用场景：</p>
 * <ul>
 *   <li>SQL 执行失败</li>
 *   <li>数据映射异常</li>
 *   <li>查询结果数量不符合预期（如 {@code selectOne} 返回多条）</li>
 *   <li>实体字段提取失败</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 */
public class DataException extends LetoolException {

    /**
     * 使用错误码和消息构造异常。
     *
     * @param errorCode 错误码（如 {@code DATA_001}）
     * @param message   错误消息
     */
    public DataException(String errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 使用错误码、消息和原始异常构造异常。
     *
     * @param errorCode 错误码
     * @param message   错误消息
     * @param cause     原始异常
     */
    public DataException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}

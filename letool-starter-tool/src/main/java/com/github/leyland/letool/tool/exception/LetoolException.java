package com.github.leyland.letool.tool.exception;

/**
 * letool 工具包统一异常基类——所有模块自定义异常必须继承此类.
 *
 * <h3>设计目标</h3>
 * <ul>
 *   <li>提供统一的 {@code errorCode} 机制，方便全局异常处理器按错误码分流</li>
 *   <li>继承 {@link RuntimeException}，无需在方法签名中声明 throws</li>
 *   <li>子类语义化分层：{@link BusinessException}（调用方问题） vs {@link SystemException}（服务端问题）</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 直接抛出
 * throw new LetoolException("E001", "配置文件加载失败");
 *
 * // 保留原始异常链
 * throw new LetoolException("E001", "配置文件加载失败", ioException);
 * }</pre>
 *
 * <h3>全局异常处理器映射建议</h3>
 * <pre>{@code
 * @ExceptionHandler(BusinessException.class)
 * public R<Void> handleBusiness(BusinessException e) {
 *     return R.fail(e.getErrorCode(), e.getMessage());
 * }
 * }</pre>
 *
 * @see BusinessException
 * @see SystemException
 */
public class LetoolException extends RuntimeException {

    /** 业务错误码，全局唯一，便于前端/调用方按码处理 */
    private final String errorCode;

    /**
     * 创建带错误码的异常.
     *
     * @param errorCode 业务错误码（全局唯一，如 "USER_001"）
     * @param message   错误描述（面向用户，非技术细节）
     */
    public LetoolException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 创建带错误码和原始异常的异常.
     *
     * @param errorCode 业务错误码
     * @param message   错误描述
     * @param cause     原始异常（保留完整堆栈信息）
     */
    public LetoolException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * 获取错误码.
     *
     * @return 创建时指定的错误码，永不为 {@code null}
     */
    public String getErrorCode() {
        return errorCode;
    }
}

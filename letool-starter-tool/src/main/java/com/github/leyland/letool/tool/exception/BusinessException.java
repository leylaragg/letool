package com.github.leyland.letool.tool.exception;

/**
 * 业务异常——因调用方参数不合法、状态不正确、前置条件不满足等原因导致操作无法继续时抛出.
 *
 * <p>此异常通常映射为 HTTP 4xx 响应码，表示调用方通过调整请求可自行修复.
 * 建议全局异常处理器将其映射到 {@code R.fail(code, message)} 返回.</p>
 *
 * <h3>典型场景</h3>
 * <ul>
 *   <li>参数校验失败（如手机号格式错误）</li>
 *   <li>业务规则不满足（如订单已支付不可重复支付）</li>
 *   <li>数据冲突（如用户名已存在）</li>
 *   <li>权限不足（如普通用户访问管理接口）</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * if (userMapper.existsByUsername(username)) {
 *     throw new BusinessException("USER_001", "用户名已存在");
 * }
 * }</pre>
 *
 * @see LetoolException
 * @see SystemException
 */
public class BusinessException extends LetoolException {

    /**
     * 创建业务异常.
     *
     * @param errorCode 业务错误码（如 "USER_001"）
     * @param message   面向用户的错误提示
     */
    public BusinessException(String errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 创建包裹原始异常的业务异常.
     *
     * @param errorCode 业务错误码
     * @param message   面向用户的错误提示
     * @param cause     原始异常（如校验框架抛出的 ConstraintViolationException）
     */
    public BusinessException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}

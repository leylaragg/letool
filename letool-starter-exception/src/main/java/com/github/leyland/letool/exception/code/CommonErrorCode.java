package com.github.leyland.letool.exception.code;

/**
 * 各 Starter 模块共享的框架级错误码。
 *
 * <p>领域模块应定义自己的错误码，不应将应用特有的异常继续加入此枚举。</p>
 */
public enum CommonErrorCode implements ErrorCode {

    /** 无法进一步分类的系统内部异常。 */
    SYSTEM_ERROR("SYS_001", "系统内部错误"),

    /** 调用方输入未通过校验或无法解析。 */
    INVALID_ARGUMENT("ARG_001", "参数不合法：{0}"),

    /** 当前依赖的服务暂时无法处理请求。 */
    SERVICE_UNAVAILABLE("SYS_002", "服务暂不可用");

    private final String code;
    private final String defaultMessage;

    CommonErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 获取稳定的框架错误码。
     *
     * @return 非空白错误码
     */
    @Override
    public String getCode() {
        return code;
    }

    /**
     * 获取框架默认消息模板。
     *
     * @return 非空白默认消息模板
     */
    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }
}

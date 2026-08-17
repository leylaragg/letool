package io.github.leylaragg.letool.web.code;

import io.github.leylaragg.letool.exception.code.ErrorCode;

/**
 * Web 模块向客户端公开的稳定错误码。
 *
 * <p>错误码用于程序判断，默认消息用于未配置国际化资源时的安全兜底。底层异常文本、
 * 请求原始值和堆栈信息不得作为默认消息的一部分。</p>
 */
public enum WebErrorCode implements ErrorCode {

    /** 请求参数或对象校验失败。 */
    VALIDATION_FAILED("WEB_400_001", "参数校验失败"),

    /** 请求缺少必填参数、请求头或 multipart 部分。 */
    MISSING_PARAMETER("WEB_400_002", "请求参数缺失"),

    /** 请求参数无法转换为目标类型。 */
    TYPE_MISMATCH("WEB_400_003", "请求参数类型不匹配"),

    /** 请求体无法被消息转换器解析。 */
    MESSAGE_NOT_READABLE("WEB_400_004", "请求体格式错误"),

    /** 调用参数违反应用前置条件。 */
    INVALID_ARGUMENT("WEB_400_005", "参数不合法"),

    /** 请求的处理器或静态资源不存在。 */
    RESOURCE_NOT_FOUND("WEB_404_001", "请求资源不存在"),

    /** 当前资源不支持请求使用的 HTTP 方法。 */
    METHOD_NOT_SUPPORTED("WEB_405_001", "请求方法不支持"),

    /** 服务端无法生成客户端接受的响应媒体类型。 */
    MEDIA_TYPE_NOT_ACCEPTABLE("WEB_406_001", "无法生成可接受的响应格式"),

    /** 请求体或上传内容超过允许大小。 */
    REQUEST_BODY_TOO_LARGE("WEB_413_001", "请求体过大"),

    /** 服务端不支持请求使用的媒体类型。 */
    MEDIA_TYPE_NOT_SUPPORTED("WEB_415_001", "请求内容类型不支持"),

    /** 未单独归类的客户端请求错误。 */
    CLIENT_ERROR("WEB_4XX_001", "请求处理失败"),

    /** 未分类或不可安全公开的服务端错误。 */
    SYSTEM_ERROR("WEB_500_001", "系统内部错误，请稍后重试");

    /** 稳定错误码。 */
    private final String code;

    /** 安全默认消息。 */
    private final String defaultMessage;

    /**
     * 创建 Web 模块错误码。
     *
     * @param code 稳定错误码
     * @param defaultMessage 安全默认消息
     */
    WebErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 获取稳定错误码。
     *
     * @return 错误码字符串
     */
    @Override
    public String getCode() {
        return code;
    }

    /**
     * 获取国际化资源不存在时使用的安全默认消息。
     *
     * @return 默认消息
     */
    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }
}

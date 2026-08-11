package com.github.leyland.letool.tool.reflection;

import com.github.leyland.letool.exception.code.ErrorCode;

/**
 * Bean、反射、类扫描和 Lambda 属性解析对外暴露的稳定错误码。
 */
public enum ReflectionErrorCode implements ErrorCode {

    /** 必填参数或公开调用契约无效。 */
    INVALID_ARGUMENT("TOOL_REFLECTION_001", "反射操作参数无效：{0}"),

    /** 指定字段或方法不存在。 */
    MEMBER_NOT_FOUND("TOOL_REFLECTION_002", "未找到反射成员：{0}"),

    /** 字段或 Bean 属性读取、写入失败。 */
    FIELD_ACCESS_FAILED("TOOL_REFLECTION_003", "字段访问失败：{0}"),

    /** 目标方法调用失败。 */
    METHOD_INVOCATION_FAILED("TOOL_REFLECTION_004", "方法调用失败：{0}"),

    /** Bean 或目标类型实例化失败。 */
    INSTANTIATION_FAILED("TOOL_REFLECTION_005", "类型实例化失败：{0}"),

    /** 类路径扫描或候选类加载失败。 */
    CLASS_SCAN_FAILED("TOOL_REFLECTION_006", "类扫描或加载失败：{0}"),

    /** Lambda 方法引用无法解析为受支持的属性。 */
    LAMBDA_RESOLUTION_FAILED("TOOL_REFLECTION_007", "Lambda 属性解析失败：{0}");

    /** 稳定机器可读错误码。 */
    private final String code;

    /** 未配置国际化资源时使用的安全默认消息。 */
    private final String defaultMessage;

    /**
     * 创建反射工具错误码。
     *
     * @param code 稳定机器可读错误码
     * @param defaultMessage 安全默认消息模板
     */
    ReflectionErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 获取稳定错误码。
     *
     * @return 非空错误码
     */
    @Override
    public String getCode() {
        return code;
    }

    /**
     * 获取安全默认消息模板。
     *
     * @return 非空默认消息模板
     */
    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }
}

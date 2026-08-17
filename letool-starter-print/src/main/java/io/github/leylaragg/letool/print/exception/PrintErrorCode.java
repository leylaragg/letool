package io.github.leylaragg.letool.print.exception;

import io.github.leylaragg.letool.exception.code.ErrorCode;

/**
 * 打印框架稳定错误码。
 *
 * @author leyland
 */
public enum PrintErrorCode implements ErrorCode {

    /** 打印请求或上下文不符合公开契约。 */
    INVALID_REQUEST("PRINT_001", "打印请求不合法：{0}"),

    /** 没有匹配模板格式的打印管线。 */
    PIPELINE_NOT_FOUND("PRINT_002", "未找到模板格式对应的打印管线：{0}"),

    /** 管线不支持请求的输出格式。 */
    OUTPUT_NOT_SUPPORTED("PRINT_003", "模板管线不支持输出格式：{0}"),

    /** 同一模板格式重复注册管线。 */
    DUPLICATE_PIPELINE("PRINT_004", "打印管线格式重复：{0}"),

    /** 通用文档模型不符合结构契约。 */
    INVALID_DOCUMENT("PRINT_005", "文档模型不合法：{0}"),

    /** 打印管线发生未分类的执行故障。 */
    PIPELINE_EXECUTION_FAILED("PRINT_006", "打印管线执行失败：{0}"),

    /** 产物超过请求声明的大小限制。 */
    OUTPUT_LIMIT_EXCEEDED("PRINT_007", "打印产物超过大小限制：{0}"),

    /** 打印管线注册信息不完整。 */
    INVALID_PIPELINE_REGISTRATION("PRINT_008", "打印管线注册不合法：{0}"),

    /** 模板源不符合格式、安全或 DSL 结构约束。 */
    TEMPLATE_COMPILATION_FAILED("PRINT_009", "打印模板编译失败：{0}"),

    /** 文档模型无法渲染为目标格式。 */
    RENDERING_FAILED("PRINT_010", "打印文档渲染失败：{0}"),

    /** 渲染结果超过请求声明的最大页数。 */
    PAGE_LIMIT_EXCEEDED("PRINT_011", "打印文档超过页数限制：{0}"),

    /** 宿主业务数据适配器发生未分类故障。 */
    ADAPTER_EXECUTION_FAILED("PRINT_012", "打印数据适配失败：{0}");

    /** 稳定错误码。 */
    private final String code;

    /** 默认中文消息模板。 */
    private final String defaultMessage;

    /** 创建打印错误码。 */
    PrintErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /** @return 稳定错误码 */
    @Override
    public String getCode() {
        return code;
    }

    /** @return 默认中文消息模板 */
    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }
}

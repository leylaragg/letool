package io.github.leylaragg.letool.ruleengine.exception;

import io.github.leylaragg.letool.exception.code.ErrorCode;

/**
 * 规则引擎核心稳定错误码。
 *
 * <p>错误码用于程序判断和对外错误协议，默认中文文案仅描述安全的
 * 错误类别，不包含规则源码、事实值或底层异常消息。</p>
 */
public enum RuleEngineErrorCode implements ErrorCode {

    /** 规则源文本超过安全限制。 */
    SOURCE_LIMIT_EXCEEDED("RULE_ENGINE_LIMIT_001", "规则源文本超过允许限制"),

    /** 规则词法单元数量超过安全限制。 */
    TOKEN_LIMIT_EXCEEDED("RULE_ENGINE_LIMIT_002", "规则词法单元数量超过允许限制"),

    /** 抽象语法树深度超过安全限制。 */
    AST_DEPTH_EXCEEDED("RULE_ENGINE_LIMIT_003", "规则表达式深度超过允许限制"),

    /** 单次求值的函数调用数量超过安全限制。 */
    FUNCTION_CALL_LIMIT_EXCEEDED("RULE_ENGINE_LIMIT_004", "函数调用次数超过允许限制"),

    /** 组件注册标识发生冲突。 */
    REGISTRATION_CONFLICT("RULE_ENGINE_API_001", "规则引擎组件注册冲突"),

    /** 调用参数不符合公开 API 契约。 */
    INVALID_ARGUMENT("RULE_ENGINE_API_002", "规则引擎参数不合法"),

    /** 表达式编译过程中发生技术故障。 */
    COMPILATION_FAILED("RULE_ENGINE_COMPILE_001", "规则表达式编译失败"),

    /** 表达式求值过程中发生技术故障。 */
    EVALUATION_FAILED("RULE_ENGINE_RUNTIME_001", "规则表达式求值失败");

    /** 稳定的机器可读错误码。 */
    private final String code;

    /** 未配置国际化文案时使用的默认中文消息。 */
    private final String defaultMessage;

    /**
     * 创建规则引擎错误码定义。
     *
     * @param code 稳定的机器可读错误码
     * @param defaultMessage 安全的默认中文消息模板
     */
    RuleEngineErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 对外错误协议使用的稳定机器码。
     *
     * @return 非空错误码
     */
    @Override
    public String getCode() {
        return code;
    }

    /**
     * 国际化资源缺失时使用的安全中文文案。
     *
     * @return 非空默认消息模板
     */
    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }
}

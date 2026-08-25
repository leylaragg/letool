package io.github.leylaragg.letool.ruleengine.diagnostic;

import io.github.leylaragg.letool.exception.code.ErrorCode;

/**
 * 规则编译、求值和规划过程的稳定诊断码。
 *
 * <p>诊断码是对外协议的一部分；消息文本可以由展示层替换，诊断码不得随文案变化。</p>
 */
public enum RuleDiagnosticCode implements ErrorCode {

    /** 源文本长度超过限制。 */
    SOURCE_LIMIT_EXCEEDED("RULE_ENGINE_LIMIT_001", "规则源文本超过允许限制"),
    /** Token 数量超过限制。 */
    TOKEN_LIMIT_EXCEEDED("RULE_ENGINE_LIMIT_002", "规则词法单元数量超过允许限制"),
    /** AST 深度超过限制。 */
    AST_DEPTH_EXCEEDED("RULE_ENGINE_LIMIT_003", "规则表达式深度超过允许限制"),
    /** 函数调用数量超过限制。 */
    FUNCTION_CALL_LIMIT_EXCEEDED("RULE_ENGINE_LIMIT_004", "函数调用次数超过允许限制"),

    /** 字符串字面量未闭合。 */
    UNTERMINATED_STRING("RULE_ENGINE_COMPILE_LEXICAL_001", "字符串字面量未闭合"),
    /** 事实路径未闭合。 */
    UNTERMINATED_PATH("RULE_ENGINE_COMPILE_LEXICAL_002", "事实路径未闭合"),
    /** 字符串包含不受支持的转义。 */
    INVALID_ESCAPE("RULE_ENGINE_COMPILE_LEXICAL_003", "字符串包含非法转义"),
    /** 出现未知字符。 */
    UNKNOWN_CHARACTER("RULE_ENGINE_COMPILE_LEXICAL_004", "规则源文本包含未知字符"),

    /** 出现不符合语法位置的 Token。 */
    UNEXPECTED_TOKEN("RULE_ENGINE_COMPILE_SYNTAX_001", "出现不符合语法位置的词法单元"),
    /** 运算符缺少操作数。 */
    MISSING_OPERAND("RULE_ENGINE_COMPILE_SYNTAX_002", "运算符缺少操作数"),
    /** 括号缺失或不匹配。 */
    MISSING_PARENTHESIS("RULE_ENGINE_COMPILE_SYNTAX_003", "表达式括号缺失或不匹配"),
    /** BETWEEN 表达式缺少 AND。 */
    MISSING_BETWEEN_AND("RULE_ENGINE_COMPILE_SYNTAX_004", "BETWEEN 表达式缺少 AND"),
    /** 裸标识符不属于阶段一语法。 */
    BARE_IDENTIFIER("RULE_ENGINE_COMPILE_SYNTAX_005", "裸标识符不属于当前规则语法"),
    /** 显式时间字面量格式无效。 */
    INVALID_TEMPORAL_LITERAL("RULE_ENGINE_COMPILE_SYNTAX_006", "显式时间字面量格式无效"),

    /** 事实路径语法无效。 */
    INVALID_FACT_PATH("RULE_ENGINE_COMPILE_SEMANTIC_001", "事实路径语法无效"),
    /** 事实契约中不存在引用的路径。 */
    UNKNOWN_FACT_PATH("RULE_ENGINE_COMPILE_SEMANTIC_002", "事实契约中不存在引用路径"),
    /** 函数目录中不存在引用的函数。 */
    UNKNOWN_FUNCTION("RULE_ENGINE_COMPILE_SEMANTIC_003", "函数目录中不存在引用函数"),
    /** 函数参数数量不匹配。 */
    ARGUMENT_COUNT_MISMATCH("RULE_ENGINE_FUNCTION_001", "函数参数数量不匹配"),
    /** 函数参数类型不匹配。 */
    ARGUMENT_TYPE_MISMATCH("RULE_ENGINE_FUNCTION_002", "函数参数类型不匹配"),
    /** 运算符两侧类型不匹配。 */
    OPERATOR_TYPE_MISMATCH("RULE_ENGINE_TYPE_001", "运算符两侧类型不匹配"),

    /** 求值所需事实缺失。 */
    MISSING_FACT_VALUE("RULE_ENGINE_EVALUATE_001", "求值所需事实缺失"),
    /** 运行期事实类型与编译契约不一致。 */
    RUNTIME_TYPE_MISMATCH("RULE_ENGINE_EVALUATE_002", "运行期事实类型与编译契约不一致"),
    /** 编译产物的执行环境摘要与当前引擎不一致。 */
    EXECUTION_ENVIRONMENT_MISMATCH(
            "RULE_ENGINE_EVALUATE_003", "编译产物与当前执行环境不一致"),
    /** 表达式求值失败。 */
    EVALUATION_ERROR("RULE_ENGINE_EVALUATE_004", "规则表达式求值失败"),
    /** 函数执行失败。 */
    FUNCTION_EXECUTION_ERROR("RULE_ENGINE_FUNCTION_003", "规则函数执行失败");

    /** 对外持久化和接口判断使用的稳定机器码，修改会破坏既有协议。 */
    private final String code;

    /** 未接入国际化资源时仍可安全展示的中文兜底文案。 */
    private final String defaultMessage;

    /**
     * 把机器码与安全兜底文案绑定为同一个枚举定义，避免解析器另维护一份易漂移的映射。
     *
     * @param code 对外协议使用的稳定机器码
     * @param defaultMessage 不含动态事实值和格式化占位符的中文兜底文案
     */
    RuleDiagnosticCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 保留早期诊断 API 的简洁访问方式，已有调用方可继续使用而无需迁移。
     *
     * @return 对外协议使用的稳定机器码
     */
    public String code() {
        return code;
    }

    /**
     * 以通用异常模块约定的方式暴露诊断机器码。
     *
     * @return 对外协议使用的稳定机器码
     */
    @Override
    public String getCode() {
        return code;
    }

    /**
     * 提供不依赖资源包的安全中文回退；动态参数由诊断格式化器在边界内追加。
     *
     * @return 不含动态参数占位符的中文兜底文案
     */
    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }
}

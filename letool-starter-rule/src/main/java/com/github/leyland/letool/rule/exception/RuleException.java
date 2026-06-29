package com.github.leyland.letool.rule.exception;

/**
 * 规则引擎异常 —— 规则引擎模块中所有异常的基类.
 *
 * <h3>设计目标</h3>
 * <ul>
 *   <li>提供统一的规则引擎异常类型，包含错误码、错误消息和规则链名称</li>
 *   <li>继承 {@link RuntimeException}，无需在方法签名中声明 throws</li>
 *   <li>携带 {@code chainName} 字段，方便定位出错的具体规则链</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 解析规则链时抛出
 * throw new RuleException("PARSE_001", "规则链 YAML 格式错误");
 *
 * // 执行规则链时抛出，关联具体链名称
 * throw new RuleException("EXEC_001", "节点执行失败", "riskChain", cause);
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class RuleException extends RuntimeException {

    /** 业务错误码，全局唯一，便于按码处理 */
    private final String errorCode;

    /** 关联的规则链名称，可能为 null */
    private final String chainName;

    // ======================== 构造方法 ========================

    /**
     * 创建仅带错误码和消息的规则引擎异常.
     *
     * @param errorCode 业务错误码（如 "PARSE_001"）
     * @param message   错误描述信息
     */
    public RuleException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.chainName = null;
    }

    /**
     * 创建带错误码、消息和原始异常的规则引擎异常.
     *
     * @param errorCode 业务错误码
     * @param message   错误描述信息
     * @param cause     原始异常（保留完整堆栈）
     */
    public RuleException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.chainName = null;
    }

    /**
     * 创建带错误码、消息、规则链名称和原始异常的规则引擎异常.
     *
     * @param errorCode 业务错误码
     * @param message   错误描述信息
     * @param chainName 关联的规则链名称
     * @param cause     原始异常
     */
    public RuleException(String errorCode, String message, String chainName, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.chainName = chainName;
    }

    // ======================== getter ========================

    /**
     * 获取错误码.
     *
     * @return 错误码，永不为 null
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * 获取关联的规则链名称.
     *
     * @return 规则链名称，可能为 null
     */
    public String getChainName() {
        return chainName;
    }
}

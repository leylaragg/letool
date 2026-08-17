package io.github.leylaragg.letool.datastructure.exception;

import io.github.leylaragg.letool.exception.code.ErrorCode;

/**
 * 数据结构与业务模式能力对外暴露的稳定错误码。
 */
public enum DataStructureErrorCode implements ErrorCode {

    /** 构建器、规则或注册参数不符合方法契约。 */
    INVALID_ARGUMENT("DATA_STRUCTURE_001", "数据结构参数无效：{0}"),

    /** 策略键被重复注册。 */
    DUPLICATE_STRATEGY_KEY("DATA_STRUCTURE_002", "策略键重复注册：{0}"),

    /** 必需策略不存在或显式替换目标不存在。 */
    STRATEGY_NOT_FOUND("DATA_STRUCTURE_003", "未找到必需策略：{0}"),

    /** 决策链没有命中规则且未配置默认动作。 */
    DECISION_NOT_MATCHED("DATA_STRUCTURE_004", "决策链没有命中可执行规则：{0}"),

    /** 树节点 ID 被重复使用。 */
    DUPLICATE_TREE_ID("DATA_STRUCTURE_005", "树节点 ID 重复：{0}"),

    /** 树节点引用了不存在的父节点。 */
    ORPHAN_TREE_NODE("DATA_STRUCTURE_006", "树节点的父节点不存在：{0}"),

    /** 树存在环或重复对象引用。 */
    INVALID_TREE_STRUCTURE("DATA_STRUCTURE_007", "树结构存在环或重复引用：{0}"),

    /** 链表连接关系不符合拓扑约束。 */
    INVALID_LINK("DATA_STRUCTURE_008", "链表连接关系无效：{0}"),

    /** 链表中检测到环。 */
    LINK_CYCLE_DETECTED("DATA_STRUCTURE_009", "链表中检测到环：{0}");

    /** 稳定的机器可读错误码。 */
    private final String code;

    /** 未配置国际化资源时使用的安全默认消息。 */
    private final String defaultMessage;

    /**
     * 创建数据结构错误码定义。
     *
     * @param code 稳定的机器可读错误码
     * @param defaultMessage 默认的人类可读消息模板
     */
    DataStructureErrorCode(String code, String defaultMessage) {
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
     * 获取安全默认消息。
     *
     * @return 非空默认消息模板
     */
    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }
}

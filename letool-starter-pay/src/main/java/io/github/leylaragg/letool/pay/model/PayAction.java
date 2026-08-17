package io.github.leylaragg.letool.pay.model;

import java.util.Map;

/**
 * 支付下单成功后需要由客户端执行的不可变动作。
 *
 * @author leyland
 * @since 2.0.0
 */
public final class PayAction {

    private static final PayAction NONE = new PayAction(PayActionType.NONE, Map.of());

    private final PayActionType type;
    private final Map<String, String> parameters;

    private PayAction(PayActionType type, Map<String, String> parameters) {
        this.type = PayModelValidator.requireObject(type, "支付动作类型");
        this.parameters = PayModelValidator.immutableCopy(parameters);
    }

    /**
     * 创建支付动作。
     *
     * @param type       动作类型
     * @param parameters 动作参数
     * @return 不可变支付动作
     */
    public static PayAction of(PayActionType type, Map<String, String> parameters) {
        return new PayAction(type, parameters);
    }

    /**
     * 获取无需额外操作的动作。
     *
     * @return 空动作单例
     */
    public static PayAction none() {
        return NONE;
    }

    /**
     * 获取动作类型。
     *
     * @return 动作类型
     */
    public PayActionType getType() {
        return type;
    }

    /**
     * 获取动作参数的不可变快照。
     *
     * @return 动作参数
     */
    public Map<String, String> getParameters() {
        return parameters;
    }

    @Override
    public String toString() {
        return "PayAction{" + "type=" + type + ", parameterCount=" + parameters.size() + '}';
    }
}

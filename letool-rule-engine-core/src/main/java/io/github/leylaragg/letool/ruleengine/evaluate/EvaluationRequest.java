package io.github.leylaragg.letool.ruleengine.evaluate;

import io.github.leylaragg.letool.ruleengine.api.CompiledExpression;
import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;
import io.github.leylaragg.letool.ruleengine.fact.RuleFacts;

/**
 * 一次表达式求值所需的完整显式输入。
 *
 * <p>请求只包含编译产物、不可变事实和求值选项。宿主身份、外部资源或可变
 * 业务会话不属于标量求值输入，调用方应提前把必要数据转换为显式事实。</p>
 *
 * @param expression 已由 Letool 编译的表达式产物
 * @param facts 本次求值读取的不可变事实快照
 * @param options 区域、时区、轨迹和单次资源限制
 */
public record EvaluationRequest(
        CompiledExpression expression,
        RuleFacts facts,
        EvaluationOptions options) {

    /**
     * 在进入环境和事实兼容校验前拒绝不完整请求。
     *
     * @param expression 已编译表达式产物
     * @param facts 本次求值的不可变事实
     * @param options 本次求值选项
     */
    public EvaluationRequest {
        if (expression == null || facts == null || options == null) {
            throw RuleEngineException.invalidArgument();
        }
    }
}

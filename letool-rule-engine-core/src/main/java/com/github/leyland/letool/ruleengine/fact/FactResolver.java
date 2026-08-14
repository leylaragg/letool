package com.github.leyland.letool.ruleengine.fact;

import com.github.leyland.letool.ruleengine.exception.RuleEngineException;

import java.util.Optional;

/**
 * 在不可变事实树中解析规范路径。
 */
public final class FactResolver {

    /**
     * 沿指定路径查询事实值。
     *
     * @param facts 规则事实树
     * @param path 规范路径
     * @return 找到的事实值；属性缺失或下标越界时为空
     */
    public Optional<FactValue> resolve(RuleFacts facts, FactPath path) {
        if (facts == null || path == null) {
            throw RuleEngineException.invalidArgument();
        }
        FactValue current = facts.root();
        for (FactPath.Segment segment : path.segments()) {
            if (segment instanceof FactPath.PropertySegment property) {
                if (!(current instanceof ObjectFactValue objectValue)) {
                    throw RuleEngineException.invalidArgument();
                }
                current = objectValue.property(property.name());
            } else if (segment instanceof FactPath.IndexSegment index) {
                if (!(current instanceof ArrayFactValue arrayValue)) {
                    throw RuleEngineException.invalidArgument();
                }
                current = arrayValue.element(index.index());
            }
            if (current == null) {
                return Optional.empty();
            }
        }
        return Optional.of(current);
    }

    /**
     * 沿指定路径读取必需事实值。
     *
     * @param facts 规则事实树
     * @param path 规范路径
     * @return 已找到事实值
     * @throws RuleEngineException 属性缺失或下标越界时抛出
     */
    public FactValue require(RuleFacts facts, FactPath path) {
        return resolve(facts, path).orElseThrow(RuleEngineException::invalidArgument);
    }
}

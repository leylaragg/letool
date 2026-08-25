package io.github.leylaragg.letool.ruleengine.api;

import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;

import java.util.List;
import java.util.Objects;

/**
 * 一次求值产生的不可变安全轨迹快照。
 */
public final class EvaluationTrace {

    private static final EvaluationTrace DISABLED = new EvaluationTrace(false, List.of(), false);

    /** 本次求值是否启用了轨迹。 */
    private final boolean enabled;

    /** 按节点完成顺序冻结的安全轨迹。 */
    private final List<TraceNode> nodes;

    /** 是否因节点预算舍弃了后续轨迹。 */
    private final boolean truncated;

    EvaluationTrace(boolean enabled, List<TraceNode> nodes, boolean truncated) {
        if (nodes == null || !enabled && (!nodes.isEmpty() || truncated)) {
            throw RuleEngineException.invalidArgument();
        }
        try {
            this.nodes = List.copyOf(nodes);
        } catch (RuntimeException exception) {
            throw RuleEngineException.invalidArgument();
        }
        this.enabled = enabled;
        this.truncated = truncated;
    }

    /**
     * 关闭轨迹时共享的空快照。
     *
     * @return 空轨迹单例
     */
    public static EvaluationTrace disabled() { return DISABLED; }

    /** @return 轨迹已启用时返回 {@code true} */
    public boolean isEnabled() { return enabled; }

    /** @return 按节点完成顺序排列的不可变轨迹 */
    public List<TraceNode> nodes() { return nodes; }

    /** @return 因节点预算停止记录时返回 {@code true} */
    public boolean isTruncated() { return truncated; }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof EvaluationTrace that
                && enabled == that.enabled && truncated == that.truncated
                && nodes.equals(that.nodes);
    }

    @Override
    public int hashCode() { return Objects.hash(enabled, nodes, truncated); }
}

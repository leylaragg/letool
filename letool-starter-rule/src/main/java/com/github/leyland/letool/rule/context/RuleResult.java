package com.github.leyland.letool.rule.context;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则引擎执行结果 —— 封装单次规则链执行后的所有输出信息.
 *
 * <h3>设计目标</h3>
 * <ul>
 *   <li>统一执行结果模型：包含成功/失败状态、执行轨迹、输出数据、耗时等</li>
 *   <li>通过静态工厂方法创建，语义清晰：{@link #success} / {@link #fail}</li>
 *   <li>与 {@link RuleContext} 中的 traces 对接，保留完整的执行轨迹</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 成功场景
 * RuleResult result = RuleResult.success("riskChain", context.getExecutionId(), context.getTraces());
 * result.setOutputs(context.getResults());
 *
 * // 失败场景
 * RuleResult result = RuleResult.fail("riskChain", context.getExecutionId(), "节点执行异常", context.getTraces());
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 * @see RuleContext
 * @see com.github.leyland.letool.rule.engine.RuleEngine
 */
public class RuleResult {

    /** 执行是否成功 */
    private final boolean success;

    /** 规则链名称 */
    private final String chainName;

    /** 执行 ID */
    private final String executionId;

    /** 执行轨迹 */
    private final List<RuleContext.NodeTrace> traces;

    /** 总耗时（毫秒） */
    private long totalDurationMs;

    /** 失败时的错误信息 */
    private String errorMessage;

    /** 输出数据（来自各节点的中间结果汇总） */
    private Map<String, Object> outputs;

    // ======================== 构造方法 ========================

    private RuleResult(boolean success, String chainName, String executionId,
                       List<RuleContext.NodeTrace> traces) {
        this.success = success;
        this.chainName = chainName;
        this.executionId = executionId;
        this.traces = traces;
        this.outputs = new HashMap<>();
    }

    // ======================== 静态工厂方法 ========================

    /**
     * 创建成功结果.
     *
     * @param chainName   规则链名称
     * @param executionId 执行 ID
     * @param traces      执行轨迹列表
     * @return 成功结果实例
     */
    public static RuleResult success(String chainName, String executionId,
                                     List<RuleContext.NodeTrace> traces) {
        RuleResult result = new RuleResult(true, chainName, executionId, traces);
        // 计算总耗时
        if (traces != null && !traces.isEmpty()) {
            long totalMs = 0;
            for (RuleContext.NodeTrace trace : traces) {
                totalMs += trace.getDurationMs();
            }
            result.totalDurationMs = totalMs;
        }
        return result;
    }

    /**
     * 创建失败结果.
     *
     * @param chainName    规则链名称
     * @param executionId  执行 ID
     * @param errorMessage 错误描述信息
     * @param traces       执行轨迹列表（包含失败节点的部分轨迹）
     * @return 失败结果实例
     */
    public static RuleResult fail(String chainName, String executionId,
                                   String errorMessage, List<RuleContext.NodeTrace> traces) {
        RuleResult result = new RuleResult(false, chainName, executionId, traces);
        result.errorMessage = errorMessage;
        if (traces != null && !traces.isEmpty()) {
            long totalMs = 0;
            for (RuleContext.NodeTrace trace : traces) {
                totalMs += trace.getDurationMs();
            }
            result.totalDurationMs = totalMs;
        }
        return result;
    }

    // ======================== getter / setter ========================

    /**
     * 判断执行是否成功.
     *
     * @return true 表示规则链所有节点执行成功
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 获取规则链名称.
     *
     * @return 规则链名称
     */
    public String getChainName() {
        return chainName;
    }

    /**
     * 获取执行 ID.
     *
     * @return UUID 格式的执行唯一标识
     */
    public String getExecutionId() {
        return executionId;
    }

    /**
     * 获取执行轨迹列表.
     *
     * @return 不可变的轨迹列表
     */
    public List<RuleContext.NodeTrace> getTraces() {
        return Collections.unmodifiableList(traces);
    }

    /**
     * 获取总耗时（毫秒）.
     *
     * @return 所有节点耗时之和
     */
    public long getTotalDurationMs() {
        return totalDurationMs;
    }

    /**
     * 设置总耗时.
     *
     * @param totalDurationMs 总耗时毫秒数
     */
    public void setTotalDurationMs(long totalDurationMs) {
        this.totalDurationMs = totalDurationMs;
    }

    /**
     * 获取错误信息.
     *
     * @return 错误描述，成功时为 null
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * 设置错误信息.
     *
     * @param errorMessage 错误描述
     */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * 获取输出数据.
     *
     * @return 各节点产生的输出数据
     */
    public Map<String, Object> getOutputs() {
        return Collections.unmodifiableMap(outputs);
    }

    /**
     * 设置输出数据.
     *
     * @param outputs 输出数据 Map
     */
    public void setOutputs(Map<String, Object> outputs) {
        if (outputs != null) {
            this.outputs = new HashMap<>(outputs);
        }
    }
}

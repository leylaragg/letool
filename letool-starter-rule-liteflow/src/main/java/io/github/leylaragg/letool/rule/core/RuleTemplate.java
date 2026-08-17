package io.github.leylaragg.letool.rule.core;

import io.github.leylaragg.letool.rule.exception.RuleException;
import com.yomahub.liteflow.core.FlowExecutor;
import com.yomahub.liteflow.flow.LiteflowResponse;

/**
 * LiteFlow 规则链执行模板。
 *
 * <p>该模板仅负责参数校验、调用 LiteFlow 原生执行器以及统一异常映射，
 * 不重复实现规则编排、组件管理和规则源加载能力。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public final class RuleTemplate {

    /** LiteFlow 原生规则执行器。 */
    private final FlowExecutor flowExecutor;

    /**
     * 创建规则链执行模板。
     *
     * @param flowExecutor LiteFlow 原生执行器
     * @throws IllegalArgumentException 当执行器为 {@code null} 时抛出
     */
    public RuleTemplate(FlowExecutor flowExecutor) {
        if (flowExecutor == null) {
            throw new IllegalArgumentException("flowExecutor must not be null");
        }
        this.flowExecutor = flowExecutor;
    }

    /**
     * 执行指定的 LiteFlow 规则链。
     *
     * <p>执行成功时直接返回 LiteFlow 原生响应；执行失败时统一转换为规则异常，
     * 避免向调用方暴露 LiteFlow 响应中的内部错误消息。</p>
     *
     * @param chainId 规则链标识
     * @param requestData 传递给规则链的请求数据，可以为 {@code null}
     * @return LiteFlow 原生执行响应
     * @throws RuleException 规则链标识为空或规则链执行失败时抛出
     */
    public LiteflowResponse execute(String chainId, Object requestData) {
        if (chainId == null || chainId.isBlank()) {
            throw RuleException.invalidChainId();
        }

        LiteflowResponse response;
        try {
            response = flowExecutor.execute2Resp(chainId, requestData);
        } catch (RuntimeException cause) {
            throw RuleException.executionFailed(chainId, cause);
        }

        // 失败响应可能没有原始异常，仍需按统一异常协议向调用方报告。
        if (response == null || !response.isSuccess()) {
            Throwable cause = response == null ? null : response.getCause();
            throw RuleException.executionFailed(chainId, cause);
        }
        return response;
    }
}

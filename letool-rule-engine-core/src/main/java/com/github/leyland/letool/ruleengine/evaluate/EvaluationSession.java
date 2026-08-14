package com.github.leyland.letool.ruleengine.evaluate;

import com.github.leyland.letool.ruleengine.exception.RuleEngineException;
import com.github.leyland.letool.ruleengine.expression.ast.AstNode;
import com.github.leyland.letool.ruleengine.fact.FactValue;
import com.github.leyland.letool.ruleengine.fact.RuleFacts;
import com.github.leyland.letool.ruleengine.function.FunctionContext;
import com.github.leyland.letool.ruleengine.type.TypeDescriptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 仅属于一次求值调用的包内可变会话。
 */
final class EvaluationSession {

    /** 只作为函数安全元数据传递的编译产物指纹。 */
    private final String expressionFingerprint;

    /** 本次调用的不可变事实快照。 */
    private final RuleFacts facts;

    /** 已由引擎门面收紧的求值选项。 */
    private final EvaluationOptions options;

    /** 可失败的宿主摘要器，其输出仍由会话净化。 */
    private final ValueSummarizer summarizer;

    /** 轨迹关闭时为 {@code null}，开启时按完成顺序累积节点。 */
    private final List<TraceNode> traceNodes;

    /** 已开始的函数调用数量。 */
    private int functionCalls;

    /** 达到轨迹节点预算后置位。 */
    private boolean traceTruncated;

    /** 创建仅属于一次求值的可变预算和轨迹会话。 */
    EvaluationSession(String expressionFingerprint, RuleFacts facts,
            EvaluationOptions options, ValueSummarizer summarizer) {
        this.expressionFingerprint = expressionFingerprint;
        this.facts = facts;
        this.options = options;
        this.summarizer = summarizer;
        this.traceNodes = options.traceEnabled() ? new ArrayList<>() : null;
    }

    /** 计入一次即将发生的函数调用并返回从一开始的调用序号。 */
    int nextFunctionInvocation(String functionCode, AstNode node) {
        functionCalls++;
        if (functionCalls > options.limits().getMaxFunctionCalls()) {
            throw new FunctionLimitFailure(
                    RuleEngineException.functionCallLimitExceeded(), functionCode, node);
        }
        return functionCalls;
    }

    /** 为当前函数调用创建上下文，并附加固定安全元数据。 */
    FunctionContext functionContext(String code, int invocationIndex) {
        return FunctionContext.of(facts, options.locale(), options.zoneId(), Map.of(
                "expressionFingerprint", expressionFingerprint,
                "functionCode", code,
                "invocationIndex", Integer.toString(invocationIndex)));
    }

    /** 记录一个成功节点；轨迹关闭或达到预算时不影响求值。 */
    void traceValue(AstNode node, FactValue value, TypeDescriptor type) {
        if (traceNodes == null) return;
        if (traceNodes.size() >= options.limits().getMaxTraceNodes()) {
            traceTruncated = true;
            return;
        }
        traceNodes.add(new TraceNode(node.getClass().getSimpleName(),
                node.startPosition(), node.endPosition(), TraceNode.ResultCategory.VALUE,
                type, summarize(value)));
    }

    /** 记录一个失败节点；摘要固定且不包含底层异常文本。 */
    void traceFailure(AstNode node) {
        if (traceNodes == null) return;
        if (traceNodes.size() >= options.limits().getMaxTraceNodes()) {
            traceTruncated = true;
            return;
        }
        traceNodes.add(new TraceNode(node.getClass().getSimpleName(),
                node.startPosition(), node.endPosition(), TraceNode.ResultCategory.FAILURE,
                com.github.leyland.letool.ruleengine.type.TypeCompatibility.unknown(), "FAILURE"));
    }

    /** 固化本次会话轨迹。 */
    EvaluationTrace trace() {
        return traceNodes == null ? EvaluationTrace.disabled()
                : new EvaluationTrace(true, traceNodes, traceTruncated);
    }

    /** 净化并截断宿主摘要器输出，摘要失败不影响求值结果。 */
    private String summarize(FactValue value) {
        int maximum = options.limits().getMaxSummaryLength();
        String result;
        try {
            result = summarizer.summarize(value, maximum);
        } catch (RuntimeException exception) {
            result = "UNAVAILABLE";
        }
        if (result == null) result = "UNAVAILABLE";
        result = sanitizeControls(result);
        if (result.length() > maximum) {
            result = maximum == 1 ? "…" : result.substring(0, maximum - 1) + "…";
        }
        return result;
    }

    /** 将摘要中的控制字符等长替换为空格。 */
    private static String sanitizeControls(String value) {
        StringBuilder safe = null;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character)) {
                if (safe == null) safe = new StringBuilder(value.length()).append(value, 0, index);
                safe.append(' ');
            } else if (safe != null) {
                safe.append(character);
            }
        }
        return safe == null ? value : safe.toString();
    }

    /** 仅用于把资源限制传递到求值结果边界的内部控制异常。 */
    static final class FunctionLimitFailure extends RuntimeException {
        /** 对外返回的固定资源限制异常。 */
        private final RuleEngineException failure;

        /** 已限制格式的函数编码。 */
        private final String functionCode;

        /** 消耗超限调用预算的 AST 节点。 */
        private final AstNode node;

        /** 创建不采集堆栈的内部资源限制控制异常。 */
        FunctionLimitFailure(
                RuleEngineException failure, String functionCode, AstNode node) {
            super(null, null, false, false);
            this.failure = failure;
            this.functionCode = functionCode;
            this.node = node;
        }

        /** @return 固定资源限制异常 */
        RuleEngineException failure() { return failure; }

        /** @return 超限调用的函数编码 */
        String functionCode() { return functionCode; }

        /** @return 超限调用节点 */
        AstNode node() { return node; }
    }
}

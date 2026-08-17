package io.github.leylaragg.letool.ruleengine.api;

import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;

/**
 * 规则编译和求值过程的不可变资源限制。
 *
 * <p>所有限制都必须是有限正数，用于防止不可信规则占用过多 CPU 或内存。</p>
 */
public final class EngineLimits {

    /** 默认源文本长度上限，按 Java 字符（UTF-16 代码单元）计。 */
    private static final int DEFAULT_MAX_SOURCE_LENGTH = 16_384;

    /** 默认最大词法单元数。 */
    private static final int DEFAULT_MAX_TOKENS = 2_048;

    /** 默认最大抽象语法树深度。 */
    private static final int DEFAULT_MAX_AST_DEPTH = 128;

    /** 默认单次求值最大函数调用数。 */
    private static final int DEFAULT_MAX_FUNCTION_CALLS = 1_024;

    /** 默认最大跟踪节点数。 */
    private static final int DEFAULT_MAX_TRACE_NODES = 4_096;

    /** 默认单个跟踪摘要最大字符数。 */
    private static final int DEFAULT_MAX_SUMMARY_LENGTH = 512;
    /** 默认最大事实深度。 */
    private static final int DEFAULT_MAX_FACT_DEPTH = 64;
    /** 默认最终规范化事实树展开节点总数。 */
    private static final int DEFAULT_MAX_FACT_NODES = 100_000;
    /** 默认单个容器最大实际元素数。 */
    private static final int DEFAULT_MAX_CONTAINER_SIZE = 10_000;

    /** 源文本长度上限，按 Java 字符（UTF-16 代码单元）计。 */
    private final int maxSourceLength;

    /** 最大词法单元数。 */
    private final int maxTokens;

    /** 最大抽象语法树深度。 */
    private final int maxAstDepth;

    /** 单次求值最大函数调用数。 */
    private final int maxFunctionCalls;

    /** 最大跟踪节点数。 */
    private final int maxTraceNodes;

    /** 单个跟踪摘要最大字符数。 */
    private final int maxSummaryLength;
    /** 最大事实深度。 */
    private final int maxFactDepth;
    /** 最大最终规范化事实树展开节点总数。 */
    private final int maxFactNodes;
    /** 单个容器最大实际元素数。 */
    private final int maxContainerSize;

    /**
     * 创建规则引擎资源限制。
     *
     * @param maxSourceLength 最大源文本字符数
     * @param maxTokens 最大词法单元数
     * @param maxAstDepth 最大抽象语法树深度
     * @param maxFunctionCalls 单次求值最大函数调用数
     * @param maxTraceNodes 最大跟踪节点数
     * @param maxSummaryLength 单个跟踪摘要最大字符数
     * @throws RuleEngineException 任一限制不是有限正数时抛出
     */
    public EngineLimits(
            int maxSourceLength,
            int maxTokens,
            int maxAstDepth,
            int maxFunctionCalls,
            int maxTraceNodes,
            int maxSummaryLength) {
        this(maxSourceLength, maxTokens, maxAstDepth, maxFunctionCalls, maxTraceNodes,
                maxSummaryLength, DEFAULT_MAX_FACT_DEPTH, DEFAULT_MAX_FACT_NODES,
                DEFAULT_MAX_CONTAINER_SIZE);
    }

    /**
     * 创建包含事实规范化预算的完整资源限制。
     *
     * @param maxSourceLength 最大源文本长度
     * @param maxTokens 最大词法单元数
     * @param maxAstDepth 最大语法树深度
     * @param maxFunctionCalls 最大函数调用数
     * @param maxTraceNodes 最大跟踪节点数
     * @param maxSummaryLength 最大摘要长度
     * @param maxFactDepth 最大事实深度
     * @param maxFactNodes 最大最终规范化事实树展开节点总数
     * @param maxContainerSize 单个容器最大实际元素数
     */
    public EngineLimits(int maxSourceLength, int maxTokens, int maxAstDepth,
            int maxFunctionCalls, int maxTraceNodes, int maxSummaryLength,
            int maxFactDepth, int maxFactNodes, int maxContainerSize) {
        this.maxSourceLength = requirePositive(maxSourceLength);
        this.maxTokens = requirePositive(maxTokens);
        this.maxAstDepth = requirePositive(maxAstDepth);
        this.maxFunctionCalls = requirePositive(maxFunctionCalls);
        this.maxTraceNodes = requirePositive(maxTraceNodes);
        this.maxSummaryLength = requirePositive(maxSummaryLength);
        this.maxFactDepth = requirePositive(maxFactDepth);
        this.maxFactNodes = requirePositive(maxFactNodes);
        this.maxContainerSize = requirePositive(maxContainerSize);
    }

    /**
     * 创建经过保守设定的默认资源限制。
     *
     * @return 可直接使用的默认限制
     */
    public static EngineLimits defaults() {
        return new EngineLimits(
                DEFAULT_MAX_SOURCE_LENGTH,
                DEFAULT_MAX_TOKENS,
                DEFAULT_MAX_AST_DEPTH,
                DEFAULT_MAX_FUNCTION_CALLS,
                DEFAULT_MAX_TRACE_NODES,
                DEFAULT_MAX_SUMMARY_LENGTH, DEFAULT_MAX_FACT_DEPTH,
                DEFAULT_MAX_FACT_NODES, DEFAULT_MAX_CONTAINER_SIZE);
    }

    /**
     * 将两组限制逐项合并为更严格的不可变限制。
     *
     * @param first 第一组限制
     * @param second 第二组限制
     * @return 每个维度均取较小正数的新限制
     * @throws RuleEngineException 任一限制为空时抛出
     */
    public static EngineLimits stricterOf(EngineLimits first, EngineLimits second) {
        if (first == null || second == null) throw RuleEngineException.invalidArgument();
        return new EngineLimits(
                Math.min(first.maxSourceLength, second.maxSourceLength),
                Math.min(first.maxTokens, second.maxTokens),
                Math.min(first.maxAstDepth, second.maxAstDepth),
                Math.min(first.maxFunctionCalls, second.maxFunctionCalls),
                Math.min(first.maxTraceNodes, second.maxTraceNodes),
                Math.min(first.maxSummaryLength, second.maxSummaryLength),
                Math.min(first.maxFactDepth, second.maxFactDepth),
                Math.min(first.maxFactNodes, second.maxFactNodes),
                Math.min(first.maxContainerSize, second.maxContainerSize));
    }

    /**
     * 源文本长度预算，按 Java 字符（UTF-16 代码单元）计。
     *
     * @return 有限正数限制
     */
    public int getMaxSourceLength() {
        return maxSourceLength;
    }

    /**
     * 单条表达式的词法单元预算。
     *
     * @return 有限正数限制
     */
    public int getMaxTokens() {
        return maxTokens;
    }

    /**
     * 解析及后续遍历共同遵守的语法树深度预算。
     *
     * @return 有限正数限制
     */
    public int getMaxAstDepth() {
        return maxAstDepth;
    }

    /**
     * 单次求值允许消耗的函数调用预算。
     *
     * @return 有限正数限制
     */
    public int getMaxFunctionCalls() {
        return maxFunctionCalls;
    }

    /**
     * 单次求值可保留的跟踪节点预算。
     *
     * @return 有限正数限制
     */
    public int getMaxTraceNodes() {
        return maxTraceNodes;
    }

    /**
     * 单个跟踪摘要的字符预算。
     *
     * @return 有限正数限制
     */
    public int getMaxSummaryLength() {
        return maxSummaryLength;
    }

    /**
     * 事实规范化允许展开的最大层数。
     *
     * @return 最大事实深度
     */
    public int getMaxFactDepth() {
        return maxFactDepth;
    }

    /**
     * 单次事实规范化允许展开的节点总预算。
     *
     * @return 最大最终规范化事实树展开节点总数
     */
    public int getMaxFactNodes() {
        return maxFactNodes;
    }

    /**
     * 单个事实容器允许接纳的元素预算。
     *
     * @return 单个容器最大实际元素数
     */
    public int getMaxContainerSize() {
        return maxContainerSize;
    }

    /**
     * 校验资源限制是正数。
     *
     * @param value 待校验数值
     * @return 已校验的正数
     * @throws RuleEngineException 数值小于或等于零时抛出
     */
    private static int requirePositive(int value) {
        if (value <= 0) {
            throw RuleEngineException.invalidArgument();
        }
        return value;
    }
}

package io.github.leylaragg.letool.ruleengine.autoconfigure;

import io.github.leylaragg.letool.ruleengine.api.EngineLimits;
import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 规则引擎的 Spring Boot 外部化配置。
 */
@ConfigurationProperties(prefix = "letool.rule-engine")
public class RuleEngineProperties {

    /** 是否启用通用规则引擎。 */
    private boolean enabled = true;

    /** 编译、求值和事实转换的资源上限。 */
    private Limits limits = new Limits();

    /**
     * Spring 条件装配使用的规则引擎开关。
     *
     * @return {@code true} 表示启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 绑定规则引擎开关。
     *
     * @param enabled {@code true} 表示启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 可继续绑定各项预算的限制对象。
     *
     * @return 可绑定的限制配置
     */
    public Limits getLimits() {
        return limits;
    }

    /**
     * 替换整组外部化资源限制。
     *
     * @param limits 非空的限制配置
     * @throws RuleEngineException 传入空对象时抛出
     */
    public void setLimits(Limits limits) {
        if (limits == null) {
            throw RuleEngineException.invalidArgument();
        }
        this.limits = limits;
    }

    /**
     * 用于外部绑定的可变资源限制。
     *
     * <p>Spring 先写入绑定值；{@link #toEngineLimits()} 创建不可变快照时再统一校验正数约束。</p>
     */
    public static class Limits {

        /** 单条规则源码上限，按 Java 字符（UTF-16 代码单元）计。 */
        private int maxSourceLength = 16_384;

        /** 词法分析允许生成的最大词法单元数。 */
        private int maxTokens = 2_048;

        /** 表达式语法树允许的最大层数。 */
        private int maxAstDepth = 128;

        /** 单次求值允许执行的最大函数调用次数。 */
        private int maxFunctionCalls = 1_024;

        /** 单次求值允许记录的最大跟踪节点数。 */
        private int maxTraceNodes = 4_096;

        /** 单个跟踪摘要允许保留的最大字符数。 */
        private int maxSummaryLength = 512;

        /** 事实值递归转换允许的最大层数。 */
        private int maxFactDepth = 64;

        /** 单次事实规范化允许展开的最大节点总数。 */
        private int maxFactNodes = 100_000;

        /** 单个容器允许包含的最大元素数。 */
        private int maxContainerSize = 10_000;

        /** @return 源码长度预算，按 UTF-16 代码单元计 */
        public int getMaxSourceLength() {
            return maxSourceLength;
        }

        /** @param maxSourceLength 绑定后的源码长度预算 */
        public void setMaxSourceLength(int maxSourceLength) {
            this.maxSourceLength = maxSourceLength;
        }

        /** @return 单条表达式的词法单元预算 */
        public int getMaxTokens() {
            return maxTokens;
        }

        /** @param maxTokens 绑定后的词法单元预算 */
        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        /** @return 解析和遍历共同遵守的语法树深度预算 */
        public int getMaxAstDepth() {
            return maxAstDepth;
        }

        /** @param maxAstDepth 绑定后的语法树深度预算 */
        public void setMaxAstDepth(int maxAstDepth) {
            this.maxAstDepth = maxAstDepth;
        }

        /** @return 单次求值的函数调用预算 */
        public int getMaxFunctionCalls() {
            return maxFunctionCalls;
        }

        /** @param maxFunctionCalls 绑定后的函数调用预算 */
        public void setMaxFunctionCalls(int maxFunctionCalls) {
            this.maxFunctionCalls = maxFunctionCalls;
        }

        /** @return 单次求值可保留的跟踪节点预算 */
        public int getMaxTraceNodes() {
            return maxTraceNodes;
        }

        /** @param maxTraceNodes 绑定后的跟踪节点预算 */
        public void setMaxTraceNodes(int maxTraceNodes) {
            this.maxTraceNodes = maxTraceNodes;
        }

        /** @return 单个跟踪摘要的字符预算 */
        public int getMaxSummaryLength() {
            return maxSummaryLength;
        }

        /** @param maxSummaryLength 绑定后的跟踪摘要字符预算 */
        public void setMaxSummaryLength(int maxSummaryLength) {
            this.maxSummaryLength = maxSummaryLength;
        }

        /** @return 事实规范化允许展开的深度预算 */
        public int getMaxFactDepth() {
            return maxFactDepth;
        }

        /** @param maxFactDepth 绑定后的事实深度预算 */
        public void setMaxFactDepth(int maxFactDepth) {
            this.maxFactDepth = maxFactDepth;
        }

        /** @return 单次事实规范化的节点总预算 */
        public int getMaxFactNodes() {
            return maxFactNodes;
        }

        /** @param maxFactNodes 绑定后的事实节点总预算 */
        public void setMaxFactNodes(int maxFactNodes) {
            this.maxFactNodes = maxFactNodes;
        }

        /** @return 单个事实容器的元素预算 */
        public int getMaxContainerSize() {
            return maxContainerSize;
        }

        /** @param maxContainerSize 绑定后的容器元素预算 */
        public void setMaxContainerSize(int maxContainerSize) {
            this.maxContainerSize = maxContainerSize;
        }

        /**
         * 按 core 构造参数顺序生成不可变限制。
         *
         * @return 可供规则引擎直接使用的资源限制
         * @throws RuleEngineException 任一限制不是正数时抛出
         */
        public EngineLimits toEngineLimits() {
            return new EngineLimits(
                    maxSourceLength,
                    maxTokens,
                    maxAstDepth,
                    maxFunctionCalls,
                    maxTraceNodes,
                    maxSummaryLength,
                    maxFactDepth,
                    maxFactNodes,
                    maxContainerSize);
        }
    }
}

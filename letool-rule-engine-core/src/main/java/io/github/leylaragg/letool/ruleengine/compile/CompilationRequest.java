package io.github.leylaragg.letool.ruleengine.compile;

import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;
import io.github.leylaragg.letool.ruleengine.type.FactContract;

/**
 * 一次表达式编译所需的完整显式输入。
 *
 * <p>函数目录、类型目录和资源限制属于已经冻结的引擎快照，不由每次调用重复传入，
 * 从而避免宿主拼出与引擎身份不一致的编译环境。</p>
 *
 * @param source 表达式源文本
 * @param factContract 本次表达式可以引用的事实类型契约
 */
public record CompilationRequest(String source, FactContract factContract) {

    /**
     * 在进入词法分析前拒绝不完整请求。
     *
     * @param source 表达式源文本
     * @param factContract 本次编译的事实类型契约
     */
    public CompilationRequest {
        if (source == null || factContract == null) {
            throw RuleEngineException.invalidArgument();
        }
    }
}

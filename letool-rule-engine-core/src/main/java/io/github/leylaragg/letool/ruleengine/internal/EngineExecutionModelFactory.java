package io.github.leylaragg.letool.ruleengine.internal;

import io.github.leylaragg.letool.ruleengine.api.EngineLimits;
import io.github.leylaragg.letool.ruleengine.api.ExecutionModelDescriptor;
import io.github.leylaragg.letool.ruleengine.function.FunctionRegistry;
import io.github.leylaragg.letool.ruleengine.internal.digest.DigestBuilder;
import io.github.leylaragg.letool.ruleengine.type.TypeCompatibility;

/**
 * 根据 core 固定语义和引擎快照配置创建完整执行模型。
 *
 * <p>编译器与门面共用这个工厂，确保独立的内部单元测试和正式引擎不会各自维护
 * 一套环境摘要组成规则。该类型位于 internal 包，不属于宿主扩展 API。</p>
 */
public final class EngineExecutionModelFactory {

    /** 当前表达式语言版本。 */
    public static final String LANGUAGE_VERSION = "1.0";

    /** 当前编译与求值成套发布的内核语义版本。 */
    public static final String SEMANTIC_VERSION = "3.0.0";

    /** 工具类不允许实例化。 */
    private EngineExecutionModelFactory() {
    }

    /**
     * 创建一个绑定函数目录和资源限制的执行模型。
     *
     * @param limits 引擎冻结的资源限制
     * @param functionRegistry 引擎冻结的函数目录
     * @return 完整执行模型
     */
    public static ExecutionModelDescriptor create(
            EngineLimits limits, FunctionRegistry functionRegistry) {
        if (limits == null || functionRegistry == null) {
            throw io.github.leylaragg.letool.ruleengine.exception.RuleEngineException
                    .invalidArgument();
        }
        return new ExecutionModelDescriptor(
                LANGUAGE_VERSION,
                SEMANTIC_VERSION,
                TypeCompatibility.TYPE_CATALOG_DIGEST,
                functionRegistry.catalogDigest(),
                compilationOptionsDigest(limits));
    }

    /** 把所有影响编译准入和结构限制的配置写入稳定摘要。 */
    private static String compilationOptionsDigest(EngineLimits limits) {
        return new DigestBuilder("LETOOL_COMPILATION_OPTIONS_V1")
                .add(limits.getMaxSourceLength())
                .add(limits.getMaxTokens())
                .add(limits.getMaxAstDepth())
                .add(limits.getMaxFunctionCalls())
                .add(limits.getMaxFactDepth())
                .add(limits.getMaxFactNodes())
                .add(limits.getMaxContainerSize())
                .finish();
    }
}

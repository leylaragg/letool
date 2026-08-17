package io.github.leylaragg.letool.ruleengine.compile;

/**
 * 为跨包门面测试构造单一语义维度变化的受控编译产物。
 */
public final class CompiledExpressionFixtures {

    private CompiledExpressionFixtures() {
    }

    /**
     * 复制产物并替换指定环境维度。
     *
     * @param source 原始产物
     * @param dimension 维度名
     * @param value 合法替换值
     * @return 只改变一个环境维度的产物
     */
    public static CompiledExpression withDimension(
            CompiledExpression source, String dimension, String value) {
        return new CompiledExpression(source.source(), source.ast(), source.resultType(),
                source.dependencies(), source.functionDependencies(),
                "languageVersion".equals(dimension) ? value : source.languageVersion(),
                "typeCatalogFingerprint".equals(dimension)
                        ? value : source.typeCatalogFingerprint(),
                "engineVersion".equals(dimension) ? value : source.engineVersion(),
                source.factContractFingerprint(),
                "functionCatalogFingerprint".equals(dimension)
                        ? value : source.functionCatalogFingerprint());
    }
}

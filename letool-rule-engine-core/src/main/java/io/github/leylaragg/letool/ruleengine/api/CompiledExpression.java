package io.github.leylaragg.letool.ruleengine.api;

import io.github.leylaragg.letool.ruleengine.compile.DependencyCoverage;
import io.github.leylaragg.letool.ruleengine.compile.ExpressionDependencies;
import io.github.leylaragg.letool.ruleengine.compile.ExpressionDependency;
import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;
import io.github.leylaragg.letool.ruleengine.expression.ast.AstNode;
import io.github.leylaragg.letool.ruleengine.internal.digest.DigestBuilder;
import io.github.leylaragg.letool.ruleengine.type.TypeDescriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 可并发读取且不包含编译时间等非语义数据的不可变表达式产物。
 *
 * <p>产物由 Letool 固定编译流水线创建，并直接绑定事实契约和完整执行环境摘要。
 * 宿主可以在同一环境中缓存它，但不能把它当作跨 Letool 大版本的持久化协议。</p>
 */
public final class CompiledExpression {

    /** 单个产物允许记录的函数依赖上限。 */
    private static final int MAX_FUNCTION_DEPENDENCIES = 1_024;

    /** 原始表达式文本，用于诊断范围和产物摘要。 */
    private final String source;

    /** 已通过语法及类型校验的规范执行树。 */
    private final AstNode ast;

    /** 编译期推导的根表达式类型。 */
    private final TypeDescriptor resultType;

    /** 带期望类型和源码范围的事实依赖。 */
    private final ExpressionDependencies dependencies;

    /** 按首次出现顺序冻结的函数编码。 */
    private final List<String> functionDependencies;

    /** 静态事实依赖是否覆盖所有运行时事实读取。 */
    private final DependencyCoverage dependencyCoverage;

    /** 编译时使用的表达式语言版本。 */
    private final String languageVersion;

    /** 编译和求值成套发布的内核语义版本。 */
    private final String semanticVersion;

    /** 编译时事实契约的内容摘要。 */
    private final String factContractDigest;

    /** 编译时完整执行模型的环境摘要。 */
    private final String environmentDigest;

    /** 覆盖产物全部语义内容的摘要。 */
    private final String artifactDigest;

    /**
     * 由包内固定编译流水线创建受控产物。
     *
     * @param source 原始表达式
     * @param ast 规范 AST
     * @param resultType 推导结果类型
     * @param dependencies 事实依赖
     * @param functionDependencies 函数依赖
     * @param dependencyCoverage 静态依赖覆盖状态
     * @param languageVersion 表达式语言版本
     * @param semanticVersion 内核语义版本
     * @param factContractDigest 事实契约摘要
     * @param environmentDigest 完整执行环境摘要
     */
    CompiledExpression(
            String source,
            AstNode ast,
            TypeDescriptor resultType,
            ExpressionDependencies dependencies,
            List<String> functionDependencies,
            DependencyCoverage dependencyCoverage,
            String languageVersion,
            String semanticVersion,
            String factContractDigest,
            String environmentDigest) {
        if (source == null || ast == null || resultType == null
                || dependencies == null || dependencyCoverage == null) {
            throw RuleEngineException.invalidArgument();
        }
        this.source = source;
        this.ast = ast;
        this.resultType = resultType;
        this.dependencies = dependencies;
        this.functionDependencies = copyFunctions(functionDependencies);
        this.dependencyCoverage = dependencyCoverage;
        this.languageVersion = requireVersion(languageVersion);
        this.semanticVersion = requireVersion(semanticVersion);
        this.factContractDigest = requireDigest(factContractDigest);
        this.environmentDigest = requireDigest(environmentDigest);
        this.artifactDigest = calculateArtifactDigest();
    }

    /** @return 原始表达式源文本 */
    public String source() {
        return source;
    }

    /** @return 唯一规范执行 AST */
    AstNode ast() {
        return ast;
    }

    /** @return 编译期结果类型 */
    public TypeDescriptor resultType() {
        return resultType;
    }

    /** @return 类型化事实依赖 */
    public ExpressionDependencies dependencies() {
        return dependencies;
    }

    /** @return 按首次出现顺序排列的函数依赖 */
    public List<String> functionDependencies() {
        return functionDependencies;
    }

    /** @return 静态依赖完整或包含动态事实访问 */
    public DependencyCoverage dependencyCoverage() {
        return dependencyCoverage;
    }

    /** @return 表达式语言版本 */
    public String languageVersion() {
        return languageVersion;
    }

    /** @return 编译与求值成套发布的内核语义版本 */
    public String semanticVersion() {
        return semanticVersion;
    }

    /** @return 编译时事实契约摘要 */
    public String factContractDigest() {
        return factContractDigest;
    }

    /** @return 编译时完整执行环境摘要 */
    public String environmentDigest() {
        return environmentDigest;
    }

    /** @return 覆盖全部产物语义维度的 SHA-256 摘要 */
    public String artifactDigest() {
        return artifactDigest;
    }

    /** 校验函数编码、拒绝重复项并冻结源码顺序。 */
    private static List<String> copyFunctions(List<String> source) {
        if (source == null) {
            throw RuleEngineException.invalidArgument();
        }
        try {
            List<String> result = new ArrayList<>();
            for (String code : source) {
                if (result.size() == MAX_FUNCTION_DEPENDENCIES
                        || code == null
                        || !code.matches("[A-Z][A-Z0-9_]{0,127}")
                        || result.contains(code)) {
                    throw RuleEngineException.invalidArgument();
                }
                result.add(code);
            }
            return Collections.unmodifiableList(result);
        } catch (RuntimeException exception) {
            throw RuleEngineException.invalidArgument();
        }
    }

    /** 限定可进入产物摘要的版本字符串格式和长度。 */
    private static String requireVersion(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
            throw RuleEngineException.invalidArgument();
        }
        return value;
    }

    /** 要求契约和环境身份为标准小写 SHA-256 文本。 */
    private static String requireDigest(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw RuleEngineException.invalidArgument();
        }
        return value;
    }

    /** 按固定顺序写入所有会影响产物含义的维度。 */
    private String calculateArtifactDigest() {
        DigestBuilder digest = new DigestBuilder("LETOOL_COMPILED_EXPRESSION_V2")
                .add(source)
                .add(AstDigest.calculate(ast))
                .add(resultType.toCanonicalString())
                .add(languageVersion)
                .add(semanticVersion)
                .add(factContractDigest)
                .add(environmentDigest)
                .add(dependencyCoverage.name())
                .add(dependencies.values().size());
        for (ExpressionDependency dependency : dependencies.values()) {
            digest.add(dependency.path().toString())
                    .add(dependency.expectedType().toCanonicalString())
                    .add(dependency.startPosition())
                    .add(dependency.endPosition());
        }
        digest.add(functionDependencies.size());
        for (String function : functionDependencies) {
            digest.add(function);
        }
        return digest.finish();
    }

    /** 相同产物摘要代表所有受控语义维度都相同。 */
    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof CompiledExpression that
                && artifactDigest.equals(that.artifactDigest);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(artifactDigest);
    }

    /** 仅展示结果类型和内容摘要，不回显规则源码。 */
    @Override
    public String toString() {
        return "CompiledExpression{" + resultType.toCanonicalString()
                + ", " + artifactDigest + "}";
    }
}

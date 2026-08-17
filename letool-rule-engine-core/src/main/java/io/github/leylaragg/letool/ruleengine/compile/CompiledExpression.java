package io.github.leylaragg.letool.ruleengine.compile;

import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;
import io.github.leylaragg.letool.ruleengine.expression.ast.AstNode;
import io.github.leylaragg.letool.ruleengine.type.TypeDescriptor;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 可并发读取且不含编译时间的不可变表达式编译产物。
 */
public final class CompiledExpression {

    /** 编译产物允许记录的函数依赖上限。 */
    private static final int MAX_FUNCTION_DEPENDENCIES = 1_024;

    /** 原始表达式文本，用于诊断范围和产物指纹。 */
    private final String source;

    /** 已通过语法及类型校验的规范执行树。 */
    private final AstNode ast;

    /** 编译期推导的根表达式类型。 */
    private final TypeDescriptor resultType;

    /** 带期望类型和源码范围的事实依赖。 */
    private final ExpressionDependencies dependencies;

    /** 按首次出现顺序冻结的函数编码。 */
    private final List<String> functionDependencies;

    /** 编译时使用的 DSL 版本。 */
    private final String languageVersion;

    /** 编译时使用的类型目录指纹。 */
    private final String typeCatalogFingerprint;

    /** 编译器实现版本。 */
    private final String engineVersion;

    /** 编译时事实契约的指纹。 */
    private final String factContractFingerprint;

    /** 编译时函数目录的指纹。 */
    private final String functionCatalogFingerprint;

    /** 覆盖以上全部执行语义的产物指纹。 */
    private final String fingerprint;

    /** 包内编译器构建受控产物，外部调用方不能伪造编译结果。 */
    CompiledExpression(String source, AstNode ast, TypeDescriptor resultType,
            ExpressionDependencies dependencies, List<String> functionDependencies,
            String languageVersion, String typeCatalogFingerprint, String engineVersion,
            String factContractFingerprint, String functionCatalogFingerprint) {
        if (source == null || ast == null || resultType == null || dependencies == null) {
            throw RuleEngineException.invalidArgument();
        }
        this.source = source;
        this.ast = ast;
        this.resultType = resultType;
        this.dependencies = dependencies;
        this.functionDependencies = copyFunctions(functionDependencies);
        this.languageVersion = requireVersion(languageVersion);
        this.typeCatalogFingerprint = requireFingerprint(typeCatalogFingerprint);
        this.engineVersion = requireVersion(engineVersion);
        this.factContractFingerprint = requireFingerprint(factContractFingerprint);
        this.functionCatalogFingerprint = requireFingerprint(functionCatalogFingerprint);
        this.fingerprint = calculateFingerprint();
    }

    /** @return 原始表达式源文本 */
    public String source() { return source; }

    /** @return 唯一规范执行 AST */
    public AstNode ast() { return ast; }

    /** @return 编译期结果类型 */
    public TypeDescriptor resultType() { return resultType; }

    /** @return 类型化事实依赖 */
    public ExpressionDependencies dependencies() { return dependencies; }

    /** @return 按首次出现顺序排列的函数依赖 */
    public List<String> functionDependencies() { return functionDependencies; }

    /** @return DSL 语言版本 */
    public String languageVersion() { return languageVersion; }

    /** @return 类型目录指纹 */
    public String typeCatalogFingerprint() { return typeCatalogFingerprint; }

    /** @return 引擎实现版本 */
    public String engineVersion() { return engineVersion; }

    /** @return 事实契约指纹 */
    public String factContractFingerprint() { return factContractFingerprint; }

    /** @return 函数目录指纹 */
    public String functionCatalogFingerprint() { return functionCatalogFingerprint; }

    /** @return 覆盖全部编译语义维度的 SHA-256 指纹 */
    public String fingerprint() { return fingerprint; }

    /** 校验函数编码、拒绝重复项并冻结源码顺序。 */
    private static List<String> copyFunctions(List<String> source) {
        if (source == null) throw RuleEngineException.invalidArgument();
        try {
            List<String> result = new ArrayList<>();
            for (String code : source) {
                if (result.size() == MAX_FUNCTION_DEPENDENCIES || code == null
                        || !code.matches("[A-Z][A-Z0-9_]{0,127}") || result.contains(code)) {
                    throw RuleEngineException.invalidArgument();
                }
                result.add(code);
            }
            return Collections.unmodifiableList(result);
        } catch (RuntimeException exception) {
            throw RuleEngineException.invalidArgument();
        }
    }

    /** 限定可进入产物指纹的版本字符串格式和长度。 */
    private static String requireVersion(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
            throw RuleEngineException.invalidArgument();
        }
        return value;
    }

    /** 要求外部目录指纹为标准小写 SHA-256 文本。 */
    private static String requireFingerprint(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw RuleEngineException.invalidArgument();
        }
        return value;
    }

    /** 按固定顺序和带长度编码生成确定性的编译产物指纹。 */
    private String calculateFingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            add(digest, "LETOOL_COMPILED_EXPRESSION_V1");
            add(digest, source);
            add(digest, AstFingerprint.calculate(ast));
            add(digest, resultType.toCanonicalString());
            add(digest, languageVersion);
            add(digest, typeCatalogFingerprint);
            add(digest, engineVersion);
            add(digest, factContractFingerprint);
            add(digest, functionCatalogFingerprint);
            addInt(digest, dependencies.values().size());
            for (ExpressionDependency dependency : dependencies.values()) {
                add(digest, dependency.path().toString());
                add(digest, dependency.expectedType().toCanonicalString());
                addInt(digest, dependency.startPosition());
                addInt(digest, dependency.endPosition());
            }
            addInt(digest, functionDependencies.size());
            for (String function : functionDependencies) add(digest, function);
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256 实现", exception);
        }
    }

    /** 以 UTF-8 长度前缀写入字符串，避免拼接边界歧义。 */
    private static void add(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        addInt(digest, bytes.length);
        digest.update(bytes);
    }

    /** 以固定四字节编码写入整数。 */
    private static void addInt(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }

    /** 相同语义指纹代表相同编译产物。 */
    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof CompiledExpression that
                && fingerprint.equals(that.fingerprint);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() { return Objects.hash(fingerprint); }

    /** 仅展示结果类型和安全指纹，不回显规则源码。 */
    @Override
    public String toString() {
        return "CompiledExpression{" + resultType.toCanonicalString() + ", " + fingerprint + "}";
    }
}

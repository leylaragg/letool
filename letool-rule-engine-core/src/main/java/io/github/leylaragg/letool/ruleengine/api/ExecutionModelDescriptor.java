package io.github.leylaragg.letool.ruleengine.api;

import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;
import io.github.leylaragg.letool.ruleengine.internal.digest.DigestBuilder;

import java.util.Objects;

/**
 * 描述一个表达式引擎快照完整且不可拆分的编译、类型和求值语义。
 *
 * <p>对象构造时一次性计算环境摘要，求值热路径只读取已经冻结的结果。语言、
 * 内核语义、类型目录、函数目录或编译选项任一维度变化后，旧产物都不能在新
 * 引擎中继续求值。</p>
 */
public final class ExecutionModelDescriptor {

    /** 执行环境摘要的格式领域，变更组成或编码时必须提升版本。 */
    private static final String DIGEST_DOMAIN = "LETOOL_EXECUTION_MODEL_V1";

    /** 表达式语言版本。 */
    private final String languageVersion;

    /** 编译与求值成套发布的内核语义版本。 */
    private final String semanticVersion;

    /** 类型兼容目录的内容摘要。 */
    private final String typeCatalogDigest;

    /** 当前引擎函数目录的内容摘要。 */
    private final String functionCatalogDigest;

    /** 影响编译准入和结构限制的选项摘要。 */
    private final String compilationOptionsDigest;

    /** 构造时冻结的完整执行环境摘要。 */
    private final String environmentDigest;

    /**
     * 验证并冻结执行模型的全部语义维度。
     *
     * @param languageVersion 表达式语言版本
     * @param semanticVersion Letool 内核语义版本
     * @param typeCatalogDigest 类型目录摘要
     * @param functionCatalogDigest 函数目录摘要
     * @param compilationOptionsDigest 编译选项摘要
     */
    public ExecutionModelDescriptor(
            String languageVersion,
            String semanticVersion,
            String typeCatalogDigest,
            String functionCatalogDigest,
            String compilationOptionsDigest) {
        this.languageVersion = requireVersion(languageVersion);
        this.semanticVersion = requireVersion(semanticVersion);
        this.typeCatalogDigest = requireDigest(typeCatalogDigest);
        this.functionCatalogDigest = requireDigest(functionCatalogDigest);
        this.compilationOptionsDigest = requireDigest(compilationOptionsDigest);
        this.environmentDigest = calculateEnvironmentDigest();
    }

    /** @return 表达式语言版本 */
    public String languageVersion() {
        return languageVersion;
    }

    /** @return 编译与求值成套发布的内核语义版本 */
    public String semanticVersion() {
        return semanticVersion;
    }

    /** @return 类型目录摘要 */
    public String typeCatalogDigest() {
        return typeCatalogDigest;
    }

    /** @return 函数目录摘要 */
    public String functionCatalogDigest() {
        return functionCatalogDigest;
    }

    /** @return 编译选项摘要 */
    public String compilationOptionsDigest() {
        return compilationOptionsDigest;
    }

    /**
     * 返回覆盖全部执行语义维度的稳定环境摘要。
     *
     * @return 六十四位小写 SHA-256 摘要
     */
    public String environmentDigest() {
        return environmentDigest;
    }

    /** 按固定字段顺序计算一次完整执行环境摘要。 */
    private String calculateEnvironmentDigest() {
        return new DigestBuilder(DIGEST_DOMAIN)
                .add(languageVersion)
                .add(semanticVersion)
                .add(typeCatalogDigest)
                .add(functionCatalogDigest)
                .add(compilationOptionsDigest)
                .finish();
    }

    /** 版本字段只接受适合进入稳定缓存身份的有限字符。 */
    private static String requireVersion(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
            throw RuleEngineException.invalidArgument();
        }
        return value;
    }

    /** 目录和选项必须使用标准小写 SHA-256 文本。 */
    private static String requireDigest(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw RuleEngineException.invalidArgument();
        }
        return value;
    }

    /** 全部组成维度相同才表示同一个执行模型。 */
    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ExecutionModelDescriptor that
                && languageVersion.equals(that.languageVersion)
                && semanticVersion.equals(that.semanticVersion)
                && typeCatalogDigest.equals(that.typeCatalogDigest)
                && functionCatalogDigest.equals(that.functionCatalogDigest)
                && compilationOptionsDigest.equals(that.compilationOptionsDigest);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(languageVersion, semanticVersion, typeCatalogDigest,
                functionCatalogDigest, compilationOptionsDigest);
    }

    /** 只展示版本和环境摘要，不展开目录内部信息。 */
    @Override
    public String toString() {
        return "ExecutionModelDescriptor{" + languageVersion + ", "
                + semanticVersion + ", " + environmentDigest + "}";
    }
}

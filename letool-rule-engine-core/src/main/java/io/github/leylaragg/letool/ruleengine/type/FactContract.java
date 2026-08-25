package io.github.leylaragg.letool.ruleengine.type;

import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;
import io.github.leylaragg.letool.ruleengine.fact.FactPath;
import io.github.leylaragg.letool.ruleengine.fact.FactPathParser;
import io.github.leylaragg.letool.ruleengine.internal.digest.DigestBuilder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.Objects;

/**
 * 声明规则可引用路径及其类型的最小不可变事实契约。
 */
public final class FactContract {

    /** 宿主声明的契约语义版本。 */
    private final String version;

    /** 按注册顺序冻结的规范路径类型。 */
    private final Map<String, TypeDescriptor> descriptors;

    /** 与注册顺序无关的契约语义摘要。 */
    private final String contractDigest;

    /** 从构建器当前状态创建隔离快照。 */
    private FactContract(String version, Map<String, TypeDescriptor> descriptors) {
        this.version = version;
        this.descriptors = Collections.unmodifiableMap(new LinkedHashMap<>(descriptors));
        this.contractDigest = calculateDigest(version, descriptors);
    }

    /**
     * 创建指定语义版本的契约构建器。
     *
     * @param version 非空白语义版本
     * @return 新构建器
     */
    public static Builder builder(String version) {
        return new Builder(version);
    }

    /**
     * 宿主用于显式演进事实契约的语义版本。
     *
     * @return 非空白版本
     */
    public String version() {
        return version;
    }

    /**
     * 查询路径类型。
     *
     * @param path 普通路径或完整插值路径
     * @return 类型描述
     */
    public Optional<TypeDescriptor> descriptor(String path) {
        return Optional.ofNullable(descriptors.get(FactPathParser.parse(path).toString()));
    }

    /**
     * 按注册顺序保存的不可修改路径类型视图。
     *
     * @return 不可修改路径类型映射
     */
    public Map<String, TypeDescriptor> descriptors() {
        return descriptors;
    }

    /**
     * 覆盖版本和排序后路径类型的契约语义摘要。
     *
     * @return 六十四位小写十六进制 SHA-256 摘要
     */
    public String contractDigest() {
        return contractDigest;
    }

    /** 排序路径后计算摘要，使构建器注册顺序不影响契约身份。 */
    private static String calculateDigest(
            String version, Map<String, TypeDescriptor> descriptors) {
        DigestBuilder digest = new DigestBuilder("LETOOL_FACT_CONTRACT_V1")
                .add(version)
                .add(descriptors.size());
        new TreeMap<>(descriptors).forEach((path, descriptor) ->
                digest.add(path).add(descriptor.toCanonicalString()));
        return digest.finish();
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof FactContract that
                && version.equals(that.version) && descriptors.equals(that.descriptors);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(version, descriptors);
    }

    /**
     * 最小事实契约的可变构建器。
     */
    public static final class Builder {

        /** 将固化到契约中的语义版本。 */
        private final String version;

        /** 按注册顺序保存的规范路径类型。 */
        private final Map<String, TypeDescriptor> descriptors = new LinkedHashMap<>();

        /** 供父子路径冲突检查复用的解析结果。 */
        private final Map<String, FactPath> paths = new LinkedHashMap<>();

        /** 创建单线程使用的契约构建器。 */
        private Builder(String version) {
            if (version == null || !version.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
                throw RuleEngineException.invalidArgument();
            }
            this.version = version;
        }

        /**
         * 注册一条无重复且无父子冲突的事实路径。
         *
         * @param source 路径文本
         * @param descriptor 非空类型描述
         * @return 当前构建器
         */
        public Builder path(String source, TypeDescriptor descriptor) {
            if (descriptor == null) {
                throw RuleEngineException.invalidArgument();
            }
            FactPath path = FactPathParser.parse(source);
            String canonical = path.toString();
            if (descriptors.containsKey(canonical)) {
                throw RuleEngineException.invalidArgument();
            }
            for (FactPath existing : paths.values()) {
                if (existing.isStrictPrefixOf(path) || path.isStrictPrefixOf(existing)) {
                    throw RuleEngineException.invalidArgument();
                }
            }
            descriptors.put(canonical, descriptor);
            paths.put(canonical, path);
            return this;
        }

        /**
         * 构建与后续构建器修改隔离的契约快照。
         *
         * @return 不可变事实契约
         */
        public FactContract build() {
            return new FactContract(version, descriptors);
        }
    }
}

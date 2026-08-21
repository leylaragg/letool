package io.github.leylaragg.letool.print.xml;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import io.github.leylaragg.letool.print.template.TemplateCompilationKey;
import io.github.leylaragg.letool.print.template.TemplateDefinition;
import io.github.leylaragg.letool.print.template.TemplateSet;
import io.github.leylaragg.letool.print.template.TemplateType;

import java.util.Objects;
import java.util.Set;

/**
 * 按集合快照和完整编译条件复用 XML 编译结果。
 *
 * <p>实例可并发使用。缓存只有容量上限，不按时间过期，装载失败不会留下条目。</p>
 *
 * @author leyland
 */
public final class XmlTemplateCompilationCache {

    /** 默认保留的模板集合编译快照数。 */
    public static final int DEFAULT_TEMPLATE_SET_CAPACITY = 64;

    /** 默认保留的完整解析快照数。 */
    public static final int DEFAULT_TEMPLATE_CAPACITY = 1024;

    /** 真正执行 XML 集合编译的无状态组件。 */
    private final XmlTemplateSetCompiler compiler;

    /** 按集合版本和摘要复用整套 XML 编译结果。 */
    private final Cache<TemplateSetCacheKey, CompiledXmlTemplateSet> templateSetCache;

    /** 按全部编译条件复用单个可打印文档。 */
    private final Cache<TemplateCompilationKey, ResolvedXmlTemplate> resolvedTemplateCache;

    /**
     * 使用框架默认容量创建双层缓存。
     *
     * @param compiler XML 模板集合编译器
     * @throws NullPointerException 编译器为空时抛出
     */
    public XmlTemplateCompilationCache(XmlTemplateSetCompiler compiler) {
        this(compiler, DEFAULT_TEMPLATE_SET_CAPACITY, DEFAULT_TEMPLATE_CAPACITY);
    }

    /**
     * 使用宿主指定的本地容量创建双层缓存。
     *
     * @param compiler XML 模板集合编译器
     * @param templateSetCapacity 模板集合缓存容量
     * @param resolvedTemplateCapacity 已解析模板缓存容量
     * @throws IllegalArgumentException 任一容量不是正整数时抛出
     * @throws NullPointerException 编译器为空时抛出
     */
    public XmlTemplateCompilationCache(
            XmlTemplateSetCompiler compiler,
            int templateSetCapacity,
            int resolvedTemplateCapacity) {
        this.compiler = Objects.requireNonNull(compiler, "compiler 不能为空");
        this.templateSetCache = newCache(requireCapacity("templateSetCapacity", templateSetCapacity));
        this.resolvedTemplateCache = newCache(
                requireCapacity("resolvedTemplateCapacity", resolvedTemplateCapacity));
    }

    /**
     * 编译或复用完整模板集合。
     *
     * @param templateSet 已发布模板集合快照
     * @return 与集合版本和摘要一致的 XML 编译结果
     * @throws PrintCompilationException XML 集合编译失败时抛出
     * @throws PrintValidationException 集合或编译结果元数据不合法时抛出
     * @throws NullPointerException 集合为空时抛出
     */
    public CompiledXmlTemplateSet compileSet(TemplateSet templateSet) {
        Objects.requireNonNull(templateSet, "templateSet 不能为空");
        TemplateSetCacheKey cacheKey = new TemplateSetCacheKey(
                templateSet.version(), templateSet.digest());
        return templateSetCache.get(cacheKey, ignored -> compileAndCheck(templateSet));
    }

    /**
     * 按完整条件解析或复用一个可打印 XML 文档。
     *
     * @param templateSet 已发布模板集合快照
     * @param compilationKey 完整编译条件
     * @return 可交给绑定和渲染流程的解析快照
     * @throws PrintCompilationException XML 集合编译失败时抛出
     * @throws PrintValidationException 编译键、集合或模板元数据不一致时抛出
     * @throws NullPointerException 任一参数为空时抛出
     */
    public ResolvedXmlTemplate resolve(
            TemplateSet templateSet, TemplateCompilationKey compilationKey) {
        Objects.requireNonNull(templateSet, "templateSet 不能为空");
        Objects.requireNonNull(compilationKey, "compilationKey 不能为空");
        TemplateDefinition definition = validateMetadata(templateSet, compilationKey);
        return resolvedTemplateCache.get(compilationKey,
                ignored -> resolveCompiledTemplate(templateSet, definition, compilationKey));
    }

    /**
     * 获取双层缓存的当前统计快照。
     *
     * @return 不暴露缓存实现类型的统计数据
     */
    public XmlTemplateCompilationCacheStats stats() {
        // Caffeine 的容量回收可能异步完成，采集前推进一次维护以获得稳定快照。
        templateSetCache.cleanUp();
        resolvedTemplateCache.cleanUp();
        return XmlTemplateCompilationCacheStats.from(templateSetCache, resolvedTemplateCache);
    }

    /**
     * 从集合编译结果中取得目标文档并核对返回元数据。
     *
     * @param templateSet 已发布集合快照
     * @param definition 目标模板定义
     * @param compilationKey 完整编译条件
     * @return 可复用的解析快照
     */
    private ResolvedXmlTemplate resolveCompiledTemplate(
            TemplateSet templateSet,
            TemplateDefinition definition,
            TemplateCompilationKey compilationKey) {
        CompiledXmlTemplate template = compileSet(templateSet).require(compilationKey.templateCode());
        PrintTemplate source = definition.template();
        if (!template.templateCode().equals(source.templateCode())
                || template.templateSetVersion() != source.templateSetVersion()
                || template.dslVersion() != source.dslVersion()
                || template.contextVersion() != source.contextVersion()) {
            throw PrintValidationException.invalidRequest("XML 模板编译结果与来源元数据不一致");
        }
        Set<String> declaredOutputs = template.inspection().declaredOutputs();
        if (!declaredOutputs.isEmpty()
                && !declaredOutputs.contains(compilationKey.outputFormat().value())) {
            throw PrintValidationException.invalidRequest(
                    "模板不允许输出格式：" + compilationKey.outputFormat().value());
        }
        return new ResolvedXmlTemplate(compilationKey, template);
    }

    /**
     * 编译模板集合，并确认编译器没有改变快照身份。
     *
     * @param templateSet 已发布集合快照
     * @return 与来源身份一致的编译集合
     */
    private CompiledXmlTemplateSet compileAndCheck(TemplateSet templateSet) {
        CompiledXmlTemplateSet compiled = compiler.compile(templateSet);
        if (compiled.templateSetVersion() != templateSet.version()
                || !compiled.templateSetDigest().equals(templateSet.digest())) {
            throw PrintValidationException.invalidRequest("XML 模板集合编译结果与来源版本不一致");
        }
        return compiled;
    }

    /**
     * 在进入缓存前核对集合身份和目标模板的稳定元数据。
     *
     * @param templateSet 已发布集合快照
     * @param compilationKey 完整编译条件
     * @return 已核对的目标模板定义
     */
    private TemplateDefinition validateMetadata(
            TemplateSet templateSet, TemplateCompilationKey compilationKey) {
        if (templateSet.version() != compilationKey.templateSetVersion()
                || !templateSet.digest().equals(compilationKey.templateSetDigest())) {
            throw PrintValidationException.invalidRequest("模板编译键与集合快照不一致");
        }
        TemplateDefinition definition = templateSet.require(compilationKey.templateCode());
        PrintTemplate template = definition.template();
        if (definition.type() != TemplateType.DOCUMENT
                || !TemplateFormat.LETOOL_XML.equals(template.templateFormat())) {
            throw PrintValidationException.invalidRequest("目标模板不是可打印的 Letool XML 文档");
        }
        if (template.templateSetVersion() != compilationKey.templateSetVersion()
                || template.dslVersion() != compilationKey.dslVersion()
                || template.contextVersion() != compilationKey.contextVersion()) {
            throw PrintValidationException.invalidRequest("模板编译键与模板元数据不一致");
        }
        return definition;
    }

    /**
     * 创建记录命中和装载结果的有界本地缓存。
     *
     * @param capacity 最大条目数
     * @return 新建的本地缓存
     */
    private static <K, V> Cache<K, V> newCache(int capacity) {
        return Caffeine.newBuilder().maximumSize(capacity).recordStats().build();
    }

    /**
     * 校验本地缓存容量。
     *
     * @param name 参数名
     * @param capacity 最大条目数
     * @return 已校验容量
     */
    private static int requireCapacity(String name, int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException(name + " 必须为正整数");
        }
        return capacity;
    }

    /**
     * 模板集合编译缓存只关心不可变快照身份。
     *
     * @author leyland
     */
    private static final class TemplateSetCacheKey {

        /** 集合版本。 */
        private final long version;

        /** 集合内容摘要。 */
        private final String digest;

        /**
         * 保存集合快照身份。
         *
         * @param version 集合版本
         * @param digest 集合内容摘要
         */
        private TemplateSetCacheKey(long version, String digest) {
            this.version = version;
            this.digest = Objects.requireNonNull(digest, "digest 不能为空");
        }

        @Override
        public boolean equals(Object object) {
            return this == object
                    || object instanceof TemplateSetCacheKey that
                    && version == that.version
                    && digest.equals(that.digest);
        }

        @Override
        public int hashCode() {
            return Objects.hash(version, digest);
        }
    }
}

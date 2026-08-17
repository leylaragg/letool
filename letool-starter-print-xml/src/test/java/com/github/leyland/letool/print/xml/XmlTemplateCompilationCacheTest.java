package com.github.leyland.letool.print.xml;

import com.github.leyland.letool.print.api.OutputFormat;
import com.github.leyland.letool.print.api.PrintTemplate;
import com.github.leyland.letool.print.api.TemplateFormat;
import com.github.leyland.letool.print.exception.PrintValidationException;
import com.github.leyland.letool.print.template.InMemoryTemplateRepository;
import com.github.leyland.letool.print.template.TemplateCompilationKey;
import com.github.leyland.letool.print.template.TemplateDefinition;
import com.github.leyland.letool.print.template.TemplateSet;
import com.github.leyland.letool.print.template.TemplateSetPublisher;
import com.github.leyland.letool.print.template.TemplateType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * XML 模板双层编译缓存测试。
 *
 * @author leyland
 */
class XmlTemplateCompilationCacheTest {

    /** 模拟独立扩展输出，确保 XML 编译缓存按完整格式隔离。 */
    private static final OutputFormat HTML = new OutputFormat("html", "text/html", "html");

    /** 相同条件应复用同一个解析快照，并留下可观测的命中记录。 */
    @Test
    void shouldReuseResolvedTemplateForSameKey() {
        TemplateSet set = set(7, "<paragraph>正文</paragraph>");
        XmlTemplateCompilationCache cache = new XmlTemplateCompilationCache(new XmlTemplateSetCompiler());
        TemplateCompilationKey key = key(set, 3, OutputFormat.PDF);

        ResolvedXmlTemplate first = cache.resolve(set, key);
        ResolvedXmlTemplate second = cache.resolve(set, key);
        XmlTemplateCompilationCacheStats stats = cache.stats();

        assertThat(second).isSameAs(first);
        assertThat(stats.templateEntries()).isEqualTo(1);
        assertThat(stats.templateMissCount()).isEqualTo(1);
        assertThat(stats.templateHitCount()).isEqualTo(1);
        assertThat(stats.templateLoadSuccessCount()).isEqualTo(1);
    }

    /** 摘要参与两层键，同版本的不同内容不能误命中。 */
    @Test
    void shouldSeparateSameVersionWithDifferentDigest() {
        TemplateSet firstSet = set(7, "<paragraph>甲</paragraph>");
        TemplateSet secondSet = set(7, "<paragraph>乙</paragraph>");
        XmlTemplateCompilationCache cache = new XmlTemplateCompilationCache(new XmlTemplateSetCompiler());

        ResolvedXmlTemplate first = cache.resolve(firstSet, key(firstSet, 3, OutputFormat.PDF));
        ResolvedXmlTemplate second = cache.resolve(secondSet, key(secondSet, 3, OutputFormat.PDF));
        XmlTemplateCompilationCacheStats stats = cache.stats();

        assertThat(second).isNotSameAs(first);
        assertThat(stats.templateSetEntries()).isEqualTo(2);
        assertThat(stats.templateEntries()).isEqualTo(2);
    }

    /** 不同文档和输出格式各有解析项，但同一集合只编译一次。 */
    @Test
    void shouldReuseSetCompilationAcrossCompleteKeys() {
        TemplateSet set = set(7,
                definition(TemplateType.DOCUMENT, "invoice", 7,
                        TemplateFormat.LETOOL_XML, "<paragraph>发票</paragraph>"),
                definition(TemplateType.DOCUMENT, "summary", 7,
                        TemplateFormat.LETOOL_XML, "<paragraph>汇总</paragraph>"));
        XmlTemplateCompilationCache cache = new XmlTemplateCompilationCache(new XmlTemplateSetCompiler());

        ResolvedXmlTemplate invoicePdf = cache.resolve(
                set, key(set, "invoice", 3, OutputFormat.PDF));
        ResolvedXmlTemplate summaryPdf = cache.resolve(
                set, key(set, "summary", 3, OutputFormat.PDF));
        ResolvedXmlTemplate invoiceHtml = cache.resolve(
                set, key(set, "invoice", 3, HTML));
        XmlTemplateCompilationCacheStats stats = cache.stats();

        assertThat(summaryPdf).isNotSameAs(invoicePdf);
        assertThat(invoiceHtml).isNotSameAs(invoicePdf);
        assertThat(stats.templateSetEntries()).isEqualTo(1);
        assertThat(stats.templateEntries()).isEqualTo(3);
        assertThat(stats.templateSetLoadSuccessCount()).isEqualTo(1);
    }

    /** 缓存只接受与来源集合和模板元数据完全一致的键。 */
    @Test
    void shouldRejectMismatchedCompilationKey() {
        TemplateSet set = set(7, "<paragraph>正文</paragraph>");
        XmlTemplateCompilationCache cache = new XmlTemplateCompilationCache(new XmlTemplateSetCompiler());

        assertThatThrownBy(() -> cache.resolve(set, new TemplateCompilationKey(
                8, set.digest(), "main", 1, 1, 3, OutputFormat.PDF)))
                .isInstanceOf(PrintValidationException.class);
        assertThatThrownBy(() -> cache.resolve(set, new TemplateCompilationKey(
                7, "f".repeat(64), "main", 1, 1, 3, OutputFormat.PDF)))
                .isInstanceOf(PrintValidationException.class);
        assertThatThrownBy(() -> cache.resolve(set, new TemplateCompilationKey(
                7, set.digest(), "main", 2, 1, 3, OutputFormat.PDF)))
                .isInstanceOf(PrintValidationException.class);
        assertThatThrownBy(() -> cache.resolve(set, new TemplateCompilationKey(
                7, set.digest(), "main", 1, 2, 3, OutputFormat.PDF)))
                .isInstanceOf(PrintValidationException.class);
        assertThatThrownBy(() -> cache.resolve(set, new TemplateCompilationKey(
                7, set.digest(), "missing", 1, 1, 3, OutputFormat.PDF)))
                .isInstanceOf(PrintValidationException.class);
    }

    /** 片段和其它模板格式不能作为 XML 文档解析。 */
    @Test
    void shouldRejectFragmentOrNonXmlTarget() {
        TemplateDefinition main = definition(TemplateType.DOCUMENT, "main", 7,
                TemplateFormat.LETOOL_XML, "<paragraph>正文</paragraph>");
        TemplateSet fragmentSet = set(7, main, definition(TemplateType.FRAGMENT, "shared", 7,
                TemplateFormat.LETOOL_XML, "<paragraph>片段</paragraph>"));
        TemplateSet jasperSet = set(8, definition(TemplateType.DOCUMENT, "main", 8,
                TemplateFormat.JASPER_JRXML, "<jasperReport/>"));
        XmlTemplateCompilationCache cache = new XmlTemplateCompilationCache(new XmlTemplateSetCompiler());

        assertThatThrownBy(() -> cache.resolve(
                fragmentSet, key(fragmentSet, "shared", 1, OutputFormat.PDF)))
                .isInstanceOf(PrintValidationException.class);
        assertThatThrownBy(() -> cache.resolve(
                jasperSet, key(jasperSet, "main", 1, OutputFormat.PDF)))
                .isInstanceOf(PrintValidationException.class);
    }

    /** 编译失败不能污染缓存，修正后的后续请求仍可正常装载。 */
    @Test
    void shouldNotCacheCompilationFailure() {
        TemplateSet invalid = set(7, "<paragraph>");
        XmlTemplateCompilationCache cache = new XmlTemplateCompilationCache(new XmlTemplateSetCompiler());

        assertThatThrownBy(() -> cache.compileSet(invalid)).isInstanceOf(PrintCompilationException.class);
        assertThatThrownBy(() -> cache.compileSet(invalid)).isInstanceOf(PrintCompilationException.class);

        XmlTemplateCompilationCacheStats failed = cache.stats();
        assertThat(failed.templateSetEntries()).isZero();
        assertThat(failed.templateSetLoadFailureCount()).isEqualTo(2);

        TemplateSet valid = set(8, "<paragraph>已修正</paragraph>");
        assertThat(cache.compileSet(valid).templateSetVersion()).isEqualTo(8);
        assertThat(cache.stats().templateSetLoadSuccessCount()).isEqualTo(1);
    }

    /** 两层缓存都按容量回收旧快照，不依赖定时过期。 */
    @Test
    void shouldBoundBothCacheLayersByCapacity() {
        XmlTemplateCompilationCache cache = new XmlTemplateCompilationCache(
                new XmlTemplateSetCompiler(), 1, 1);
        TemplateSet first = set(1, "<paragraph>一</paragraph>");
        TemplateSet second = set(2, "<paragraph>二</paragraph>");

        cache.resolve(first, key(first, 1, OutputFormat.PDF));
        cache.resolve(second, key(second, 1, OutputFormat.PDF));

        assertThat(cache.stats().templateSetEntries()).isLessThanOrEqualTo(1);
        assertThat(cache.stats().templateEntries()).isLessThanOrEqualTo(1);
    }

    /** 容量必须由宿主明确配置为正整数。 */
    @Test
    void shouldRejectInvalidCapacity() {
        XmlTemplateSetCompiler compiler = new XmlTemplateSetCompiler();

        assertThatThrownBy(() -> new XmlTemplateCompilationCache(compiler, 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new XmlTemplateCompilationCache(compiler, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(XmlTemplateCompilationCache.DEFAULT_TEMPLATE_SET_CAPACITY).isEqualTo(64);
        assertThat(XmlTemplateCompilationCache.DEFAULT_TEMPLATE_CAPACITY).isEqualTo(1024);
    }

    /** 创建与模板集合元数据一致的完整编译键。 */
    private TemplateCompilationKey key(
            TemplateSet set, long rendererProfileVersion, OutputFormat outputFormat) {
        return key(set, "main", rendererProfileVersion, outputFormat);
    }

    /** 为集合中的指定模板创建完整编译键。 */
    private TemplateCompilationKey key(
            TemplateSet set,
            String templateCode,
            long rendererProfileVersion,
            OutputFormat outputFormat) {
        PrintTemplate template = set.require(templateCode).template();
        return new TemplateCompilationKey(set.version(), set.digest(), template.templateCode(),
                template.dslVersion(), template.contextVersion(), rendererProfileVersion, outputFormat);
    }

    /** 创建一个只含主文档的测试集合。 */
    private TemplateSet set(long version, String blocks) {
        return set(version, definition(
                TemplateType.DOCUMENT, "main", version, TemplateFormat.LETOOL_XML, blocks));
    }

    /** 创建包含指定定义的测试集合。 */
    private TemplateSet set(long version, TemplateDefinition... definitions) {
        return new TemplateSetPublisher(new InMemoryTemplateRepository(), List.of())
                .publish(version, List.of(definitions));
    }

    /** 创建可调整用途和格式的测试模板定义。 */
    private TemplateDefinition definition(
            TemplateType type,
            String code,
            long version,
            TemplateFormat format,
            String blocks) {
        String source = format.equals(TemplateFormat.LETOOL_XML)
                ? "<" + (type == TemplateType.DOCUMENT ? "document" : "fragment")
                + " xmlns=\"" + XmlDsl.NAMESPACE_V1 + "\""
                + (type == TemplateType.DOCUMENT ? " context-version=\"1\"><page>" : ">")
                + blocks + (type == TemplateType.DOCUMENT ? "</page></document>" : "</fragment>")
                : blocks;
        PrintTemplate template = new PrintTemplate(
                code, format, 1, version, 1, source.getBytes(StandardCharsets.UTF_8));
        return new TemplateDefinition(type, template);
    }
}

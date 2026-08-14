package com.github.leyland.letool.print.xml;

import com.github.leyland.letool.print.api.PrintTemplate;
import com.github.leyland.letool.print.api.OutputFormat;
import com.github.leyland.letool.print.api.TemplateFormat;
import com.github.leyland.letool.print.exception.PrintValidationException;
import com.github.leyland.letool.print.template.InMemoryTemplateRepository;
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
 * XML 模板集合发布校验器测试。
 *
 * @author leyland
 */
class XmlTemplateSetValidatorTest {

    /** 合法引用图应通过发布 SPI 并完整写入仓库。 */
    @Test
    void shouldValidateBeforePublishingTemplateSet() {
        InMemoryTemplateRepository repository = new InMemoryTemplateRepository();
        TemplateSetPublisher publisher = new TemplateSetPublisher(
                repository, List.of(new XmlTemplateSetValidator()));

        TemplateSet published = publisher.publish(1, List.of(
                document("<include template=\"shared\"/>"),
                fragment("shared", "<paragraph>ok</paragraph>")));

        assertThat(repository.find(1)).containsSame(published);
    }

    /** 非法引用不得留下半发布版本，错误只携带安全编译详情。 */
    @Test
    void shouldRejectInvalidGraphWithoutPublishing() {
        InMemoryTemplateRepository repository = new InMemoryTemplateRepository();
        TemplateSetPublisher publisher = new TemplateSetPublisher(
                repository, List.of(new XmlTemplateSetValidator()));

        assertThatThrownBy(() -> publisher.publish(1, List.of(
                document("<include template=\"missing\"/>"))))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("XML 模板集合校验失败")
                .hasMessageContaining("missing")
                .hasMessageNotContaining("<include");
        assertThat(repository.find(1)).isEmpty();
    }

    /** 编译异常应保留框架生成的安全详情。 */
    @Test
    void shouldExposeSafeCompilationDetail() {
        PrintCompilationException exception = PrintCompilationException.invalid("main：安全详情");

        assertThat(exception.detail()).isEqualTo("main：安全详情");
    }

    /** 发布校验与运行时共用缓存时，合法集合无需再次完成整套编译。 */
    @Test
    void shouldShareCompilationCacheWithRuntimeResolution() {
        InMemoryTemplateRepository repository = new InMemoryTemplateRepository();
        XmlTemplateCompilationCache cache = new XmlTemplateCompilationCache(new XmlTemplateSetCompiler());
        TemplateSetPublisher publisher = new TemplateSetPublisher(
                repository, List.of(XmlTemplateSetValidator.using(cache)));
        TemplateSet published = publisher.publishAndActivate(1, List.of(
                document("<paragraph>正文</paragraph>")));
        XmlTemplateCompilationService service = new XmlTemplateCompilationService(repository, cache);

        ResolvedXmlTemplate resolved = service.resolveCurrent("main", 1, OutputFormat.PDF);

        assertThat(resolved.key().templateSetDigest()).isEqualTo(published.digest());
        assertThat(cache.stats().templateSetLoadSuccessCount()).isEqualTo(1);
        assertThat(cache.stats().templateSetHitCount()).isEqualTo(1);
    }

    /** 发布校验失败不进入共享缓存，后续校验仍会重新编译。 */
    @Test
    void shouldRetryFailedCompilationThroughSharedValidator() {
        XmlTemplateCompilationCache cache = new XmlTemplateCompilationCache(new XmlTemplateSetCompiler());
        XmlTemplateSetValidator validator = XmlTemplateSetValidator.using(cache);
        TemplateSet invalid = new TemplateSetPublisher(new InMemoryTemplateRepository(), List.of())
                .publish(1, List.of(document("<paragraph>")));

        assertThatThrownBy(() -> validator.validate(invalid))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageNotContaining("<paragraph>");
        assertThatThrownBy(() -> validator.validate(invalid))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageNotContaining("<paragraph>");

        assertThat(cache.stats().templateSetLoadFailureCount()).isEqualTo(2);
        assertThat(cache.stats().templateSetEntries()).isZero();
    }

    /** 把块级 XML 包装成主文档定义。 */
    private TemplateDefinition document(String blocks) {
        String source = "<document xmlns=\"" + XmlDsl.NAMESPACE_V1
                + "\" context-version=\"1\"><page>" + blocks + "</page></document>";
        return definition(TemplateType.DOCUMENT, "main", source);
    }

    /** 把块级 XML 包装成指定代码的片段定义。 */
    private TemplateDefinition fragment(String code, String blocks) {
        return definition(TemplateType.FRAGMENT, code,
                "<fragment xmlns=\"" + XmlDsl.NAMESPACE_V1 + "\">"
                        + blocks + "</fragment>");
    }

    /** 将 XML 源转换为测试使用的模板定义。 */
    private TemplateDefinition definition(TemplateType type, String code, String source) {
        PrintTemplate template = new PrintTemplate(code, TemplateFormat.LETOOL_XML,
                1, 1, 1, source.getBytes(StandardCharsets.UTF_8));
        return new TemplateDefinition(type, template);
    }
}

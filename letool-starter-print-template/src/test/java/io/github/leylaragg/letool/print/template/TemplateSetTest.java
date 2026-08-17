package io.github.leylaragg.letool.print.template;

import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 模板集合构造与不可变性测试。
 *
 * @author leyland
 */
class TemplateSetTest {

    /** 用于验证开放模板格式也会进入摘要。 */
    private static final TemplateFormat OTHER_FORMAT = new TemplateFormat("other-template");

    /** 集合应按模板代码排序并隔离调用方集合。 */
    @Test
    void shouldCreateSortedImmutableTemplateSet() {
        List<TemplateDefinition> source = new ArrayList<>(List.of(
                fragment("z-fragment", 7, "Z"),
                document("main", 7, "MAIN")));

        TemplateSet set = TemplateSetFactory.standard().create(7, source);
        source.clear();

        assertThat(set.version()).isEqualTo(7);
        assertThat(set.templateCodes()).containsExactly("main", "z-fragment");
        assertThat(set.require("main").type()).isEqualTo(TemplateType.DOCUMENT);
        assertThat(set.find("missing")).isEmpty();
        assertThat(set.documentCount()).isEqualTo(1);
        assertThat(set.fragmentCount()).isEqualTo(1);
        assertThatThrownBy(() -> set.definitions().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> set.templateCodes().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /** 集合必须包含文档且代码、版本保持一致。 */
    @Test
    void shouldRejectInvalidCollectionStructure() {
        TemplateSetFactory factory = TemplateSetFactory.standard();

        assertThatThrownBy(() -> factory.create(1, List.of(
                fragment("fragment", 1, "F"))))
                .isInstanceOf(PrintValidationException.class);
        assertThatThrownBy(() -> factory.create(1, List.of(
                document("same", 1, "A"), fragment("same", 1, "B"))))
                .isInstanceOf(PrintValidationException.class);
        assertThatThrownBy(() -> factory.create(1, List.of(
                document("main", 2, "A"))))
                .isInstanceOf(PrintValidationException.class);
        assertThatThrownBy(() -> factory.create(0, List.of(
                document("main", 1, "A"))))
                .isInstanceOf(PrintValidationException.class);
        assertThatThrownBy(() -> factory.create(1, List.of()))
                .isInstanceOf(PrintValidationException.class);
    }

    /** Java API 的空值错误应在集合构造边界暴露。 */
    @Test
    void shouldRejectNullCollectionOrDefinition() {
        TemplateSetFactory factory = TemplateSetFactory.standard();

        assertThatNullPointerException().isThrownBy(() -> factory.create(1, null));
        List<TemplateDefinition> definitions = new ArrayList<>();
        definitions.add(document("main", 1, "A"));
        definitions.add(null);
        assertThatNullPointerException().isThrownBy(() -> factory.create(1, definitions));
    }

    /** 输入顺序不同不应影响集合摘要。 */
    @Test
    void shouldCalculateOrderIndependentDigest() {
        TemplateDefinition main = document("main", 9, "MAIN");
        TemplateDefinition fragment = fragment("fragment", 9, "F");

        TemplateSet first = TemplateSetFactory.standard().create(
                9, List.of(main, fragment));
        TemplateSet second = TemplateSetFactory.standard().create(
                9, List.of(fragment, main));

        assertThat(first.digest()).matches("[0-9a-f]{64}");
        assertThat(second.digest()).isEqualTo(first.digest());
    }

    /** 影响模板语义的元数据和正文都应进入摘要。 */
    @Test
    void shouldIncludeSemanticMetadataAndContentInDigest() {
        String base = createSingle(1, document("main", 1, "A")).digest();

        assertThat(createSingle(2, document("main", 2, "A")).digest())
                .isNotEqualTo(base);
        assertThat(createSingle(1, document("main", 1, "B")).digest())
                .isNotEqualTo(base);
        assertThat(createSingle(1, definition(TemplateType.DOCUMENT, "main", 1,
                OTHER_FORMAT, 1, 1, "A")).digest())
                .isNotEqualTo(base);
        assertThat(createSingle(1, definition(TemplateType.DOCUMENT, "main", 1,
                TemplateFormat.LETOOL_XML, 2, 1, "A")).digest())
                .isNotEqualTo(base);
        assertThat(createSingle(1, definition(TemplateType.DOCUMENT, "main", 1,
                TemplateFormat.LETOOL_XML, 1, 2, "A")).digest())
                .isNotEqualTo(base);
    }

    /** 模板用途变化也必须改变集合摘要。 */
    @Test
    void shouldIncludeTemplateTypeInDigest() {
        TemplateDefinition main = document("main", 1, "MAIN");
        TemplateDefinition fragment = fragment("shared", 1, "SHARED");
        TemplateDefinition document = document("shared", 1, "SHARED");

        String fragmentDigest = TemplateSetFactory.standard()
                .create(1, List.of(main, fragment)).digest();
        String documentDigest = TemplateSetFactory.standard()
                .create(1, List.of(main, document)).digest();

        assertThat(documentDigest).isNotEqualTo(fragmentDigest);
    }

    /** 工厂应在发布前执行模板数量和总字节数限制。 */
    @Test
    void shouldEnforceTemplateCountAndTotalBytes() {
        TemplateSetFactory countLimited = new TemplateSetFactory(1, 100);
        TemplateSetFactory bytesLimited = new TemplateSetFactory(10, 3);

        assertThatThrownBy(() -> countLimited.create(1, List.of(
                document("main", 1, "A"), fragment("fragment", 1, "B"))))
                .isInstanceOf(PrintValidationException.class);
        assertThatThrownBy(() -> bytesLimited.create(1, List.of(
                document("main", 1, "ABCD"))))
                .isInstanceOf(PrintValidationException.class);
        assertThat(new TemplateSetFactory(1, 1)
                .create(1, List.of(document("main", 1, "A"))).digest())
                .matches("[0-9a-f]{64}");
    }

    /** 查询不存在的模板时应返回安全的业务异常。 */
    @Test
    void shouldRejectMissingRequiredTemplate() {
        TemplateSet set = createSingle(1, document("main", 1, "A"));

        assertThatThrownBy(() -> set.require("missing"))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageNotContaining("A");
    }

    /** 创建包含片段的单文档集合，便于比较类型摘要。 */
    private TemplateSet createSingle(long version, TemplateDefinition definition) {
        if (definition.type() == TemplateType.DOCUMENT) {
            return TemplateSetFactory.standard().create(version, List.of(definition));
        }
        return TemplateSetFactory.standard().create(version, List.of(
                document("host", version, "HOST"), definition));
    }

    /** 创建文档模板。 */
    private TemplateDefinition document(String code, long version, String source) {
        return definition(TemplateType.DOCUMENT, code, version,
                TemplateFormat.LETOOL_XML, 1, 1, source);
    }

    /** 创建片段模板。 */
    private TemplateDefinition fragment(String code, long version, String source) {
        return definition(TemplateType.FRAGMENT, code, version,
                TemplateFormat.LETOOL_XML, 1, 1, source);
    }

    /** 创建可调整语义元数据的模板定义。 */
    private TemplateDefinition definition(
            TemplateType type,
            String code,
            long version,
            TemplateFormat format,
            int dslVersion,
            int contextVersion,
            String source) {
        PrintTemplate template = new PrintTemplate(code, format, dslVersion,
                version, contextVersion, source.getBytes(StandardCharsets.UTF_8));
        return new TemplateDefinition(type, template);
    }
}

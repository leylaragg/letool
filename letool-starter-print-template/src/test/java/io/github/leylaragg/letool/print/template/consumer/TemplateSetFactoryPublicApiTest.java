package io.github.leylaragg.letool.print.template.consumer;

import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.template.TemplateDefinition;
import io.github.leylaragg.letool.print.template.TemplateSet;
import io.github.leylaragg.letool.print.template.TemplateSetFactory;
import io.github.leylaragg.letool.print.template.TemplateType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 从框架使用方的包验证模板集合恢复入口。
 *
 * @author leyland
 */
class TemplateSetFactoryPublicApiTest {

    /** 数据库等外部来源可以通过标准工厂恢复受治理的模板集合。 */
    @Test
    void shouldRestorePersistedDefinitionsThroughPublicFactory() {
        long version = 7;
        PrintTemplate template = new PrintTemplate("main", TemplateFormat.LETOOL_XML, 1, version, 1,
                "<document/>".getBytes(StandardCharsets.UTF_8));
        TemplateDefinition definition = new TemplateDefinition(TemplateType.DOCUMENT, template);

        TemplateSet restored = TemplateSetFactory.standard().create(version, List.of(definition));

        assertThat(restored.version()).isEqualTo(version);
        assertThat(restored.templateCodes()).containsExactly("main");
        assertThat(restored.digest()).matches("[0-9a-f]{64}");
    }

    /** 调用方只能使用标准限制，不能自行放宽集合治理上限。 */
    @Test
    void shouldKeepConstructorsOutsidePublicApi() {
        assertThat(TemplateSetFactory.class.getConstructors()).isEmpty();
        assertThat(TemplateSet.class.getConstructors()).isEmpty();
    }
}

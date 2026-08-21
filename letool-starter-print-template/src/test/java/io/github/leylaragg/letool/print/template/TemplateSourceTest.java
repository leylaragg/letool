package io.github.leylaragg.letool.print.template;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 只读模板来源与可写仓库的职责关系测试。
 *
 * @author leyland
 */
class TemplateSourceTest {

    /** 仓库在提供发布能力的同时也应满足全部读取入口。 */
    @Test
    void shouldTreatRepositoryAsTemplateSource() {
        TemplateSource source = new InMemoryTemplateRepository();

        assertThat(source.current()).isEqualTo(Optional.empty());
        assertThat(TemplateSource.class).isAssignableFrom(TemplateRepository.class);
    }
}

package com.github.leyland.letool.print.template;

import com.github.leyland.letool.print.api.OutputFormat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 模板编译键的值语义与参数边界测试。
 *
 * @author leyland
 */
class TemplateCompilationKeyTest {

    /** 测试使用的标准集合摘要。 */
    private static final String DIGEST = "0123456789abcdef0123456789abcdef"
            + "0123456789abcdef0123456789abcdef";

    /** 相同编译条件应得到相同键，不同条件不能相互命中。 */
    @Test
    void shouldCompareEveryCompilationCondition() {
        TemplateCompilationKey key = key(7, DIGEST, "invoice", 2, 3, 4, OutputFormat.PDF);

        assertThat(key).isEqualTo(key(7, DIGEST, "invoice", 2, 3, 4, OutputFormat.PDF));
        assertThat(key.hashCode()).isEqualTo(
                key(7, DIGEST, "invoice", 2, 3, 4, OutputFormat.PDF).hashCode());
        assertThat(key).isNotEqualTo(key(8, DIGEST, "invoice", 2, 3, 4, OutputFormat.PDF));
        assertThat(key).isNotEqualTo(key(7, "f".repeat(64), "invoice", 2, 3, 4, OutputFormat.PDF));
        assertThat(key).isNotEqualTo(key(7, DIGEST, "receipt", 2, 3, 4, OutputFormat.PDF));
        assertThat(key).isNotEqualTo(key(7, DIGEST, "invoice", 5, 3, 4, OutputFormat.PDF));
        assertThat(key).isNotEqualTo(key(7, DIGEST, "invoice", 2, 5, 4, OutputFormat.PDF));
        assertThat(key).isNotEqualTo(key(7, DIGEST, "invoice", 2, 3, 5, OutputFormat.PDF));
        assertThat(key).isNotEqualTo(key(7, DIGEST, "invoice", 2, 3, 4, OutputFormat.DOCX));
    }

    /** 访问器应完整返回构造编译键时锁定的条件。 */
    @Test
    void shouldExposeStableCompilationConditions() {
        TemplateCompilationKey key = key(7, DIGEST, "invoice", 2, 3, 4, OutputFormat.PDF);

        assertThat(key.templateSetVersion()).isEqualTo(7);
        assertThat(key.templateSetDigest()).isEqualTo(DIGEST);
        assertThat(key.templateCode()).isEqualTo("invoice");
        assertThat(key.dslVersion()).isEqualTo(2);
        assertThat(key.contextVersion()).isEqualTo(3);
        assertThat(key.rendererProfileVersion()).isEqualTo(4);
        assertThat(key.outputFormat()).isEqualTo(OutputFormat.PDF);
        assertThat(key.toString()).contains("invoice", DIGEST).doesNotContain("templateBody");
    }

    /** 不完整或不稳定的条件不能进入编译缓存。 */
    @Test
    void shouldRejectInvalidCompilationConditions() {
        assertThatThrownBy(() -> key(0, DIGEST, "invoice", 2, 3, 4, OutputFormat.PDF))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> key(7, "ABCDEF".repeat(10) + "ABCD", "invoice", 2, 3, 4,
                OutputFormat.PDF)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> key(7, "0".repeat(63), "invoice", 2, 3, 4, OutputFormat.PDF))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> key(7, DIGEST, " ", 2, 3, 4, OutputFormat.PDF))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> key(7, DIGEST, "x".repeat(129), 2, 3, 4, OutputFormat.PDF))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> key(7, DIGEST, "invoice", 0, 3, 4, OutputFormat.PDF))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> key(7, DIGEST, "invoice", 2, 0, 4, OutputFormat.PDF))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> key(7, DIGEST, "invoice", 2, 3, 0, OutputFormat.PDF))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> key(7, DIGEST, "invoice", 2, 3, 4, null))
                .isInstanceOf(NullPointerException.class);
    }

    /** 创建一组可单独调整的编译条件。 */
    private TemplateCompilationKey key(
            long templateSetVersion,
            String templateSetDigest,
            String templateCode,
            int dslVersion,
            int contextVersion,
            long rendererProfileVersion,
            OutputFormat outputFormat) {
        return new TemplateCompilationKey(templateSetVersion, templateSetDigest, templateCode,
                dslVersion, contextVersion, rendererProfileVersion, outputFormat);
    }
}

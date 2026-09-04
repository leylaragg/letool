package io.github.leylaragg.letool.print.api;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证打印元数据统一校验与防御性复制契约。 */
class PrintMetadataTest {

    /** 空元数据引用应保留稳定异常消息。 */
    @Test
    void rejectsNullMetadata() {
        assertThatThrownBy(() -> PrintMetadata.copyOf(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("metadata 不能为空");
    }

    /** 空白键、空键和空值均应拒绝并返回统一消息。 */
    @Test
    void rejectsBlankOrNullKeysAndNullValues() {
        assertThatThrownBy(() -> PrintMetadata.copyOf(Map.of(" ", "v")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("metadata 不允许空键或空值");
        Map<String, String> nullKey = new LinkedHashMap<>();
        nullKey.put(null, "v");
        assertThatThrownBy(() -> PrintMetadata.copyOf(nullKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("metadata 不允许空键或空值");
        Map<String, String> nullValue = new LinkedHashMap<>();
        nullValue.put("k", null);
        assertThatThrownBy(() -> PrintMetadata.copyOf(nullValue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("metadata 不允许空键或空值");
    }

    /** 空集合可复制，非空集合与调用方隔离且结果不可修改。 */
    @Test
    void copiesEmptyAndInputMetadataDefensively() {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("k", "v");
        Map<String, String> copy = PrintMetadata.copyOf(source);
        source.put("other", "value");
        assertThat(copy).containsExactly(Map.entry("k", "v"));
        assertThat(PrintMetadata.copyOf(Map.of())).isEmpty();
        assertThatThrownBy(() -> copy.put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

package com.github.leyland.letool.print.xml.tag;

import com.github.leyland.letool.print.document.node.ParagraphNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 自定义标签注册表契约测试。
 *
 * @author leyland
 */
class PrintTagRegistryTest {

    /** 验证注册表冻结处理器集合、属性白名单和名称视图。 */
    @Test
    void shouldCreateImmutableTagSnapshot() {
        List<PrintTagHandler> handlers = new ArrayList<>();
        handlers.add(handler("notice", Set.of("level")));
        PrintTagRegistry registry = new PrintTagRegistry(handlers);

        handlers.clear();

        PrintTagHandler registered = registry.require("notice");
        assertThat(registered.tagName()).isEqualTo("notice");
        assertThat(registered.allowedAttributes()).containsExactly("level");
        assertThat(registry.tagNames()).containsExactly("notice");
        assertThatThrownBy(() -> registry.tagNames().add("other"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /** 验证非法名称、重复名称、内置名称和非法属性均被拒绝。 */
    @Test
    void shouldRejectUnsafeRegistrations() {
        assertThatThrownBy(() -> new PrintTagRegistry(List.of(handler("Notice", Set.of()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("名称");
        assertThatThrownBy(() -> new PrintTagRegistry(List.of(
                handler("notice", Set.of()), handler("notice", Set.of()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复");
        assertThatThrownBy(() -> new PrintTagRegistry(List.of(handler("paragraph", Set.of()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("内置");
        assertThatThrownBy(() -> new PrintTagRegistry(List.of(
                handler("notice", Set.of("class-name")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("属性");
        assertThatThrownBy(() -> new PrintTagRegistry(List.of(
                handler("notice", Set.of("handler-class")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("属性");
        assertThatThrownBy(() -> new PrintTagRegistry(List.of(
                handler("notice", Set.of("implementation")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("属性");
    }

    /** 验证未知标签不会被静默忽略。 */
    @Test
    void shouldRejectMissingTag() {
        PrintTagRegistry registry = new PrintTagRegistry(List.of());

        assertThatThrownBy(() -> registry.require("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不存在");
    }

    /** 创建块级无子节点测试处理器。 */
    private static PrintTagHandler handler(String name, Set<String> attributes) {
        return new PrintTagHandler() {
            @Override
            public String tagName() {
                return name;
            }

            @Override
            public TagPlacement placement() {
                return TagPlacement.BLOCK;
            }

            @Override
            public TagContentModel contentModel() {
                return TagContentModel.EMPTY;
            }

            @Override
            public Set<String> allowedAttributes() {
                return attributes;
            }

            @Override
            public PrintTagPlan compile(TagCompileContext context) {
                return binding -> new ParagraphNode("", List.of());
            }
        };
    }
}

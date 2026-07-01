package com.github.leyland.letool.sensitive.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MaskContext 测试")
class MaskContextTest {

    @Nested
    @DisplayName("DEFAULT 常量")
    class DefaultConstantTests {

        @Test
        @DisplayName("DEFAULT 应使用 * 作为默认遮盖字符")
        void shouldUseAsteriskByDefault() {
            assertEquals('*', MaskContext.DEFAULT.getMaskChar());
        }

        @Test
        @DisplayName("DEFAULT 的 keepPrefix 应为 -1（表示使用策略默认值）")
        void shouldHaveDefaultKeepPrefix() {
            assertEquals(-1, MaskContext.DEFAULT.getKeepPrefix());
        }

        @Test
        @DisplayName("DEFAULT 的 keepSuffix 应为 -1")
        void shouldHaveDefaultKeepSuffix() {
            assertEquals(-1, MaskContext.DEFAULT.getKeepSuffix());
        }
    }

    @Nested
    @DisplayName("Builder 链式构造")
    class BuilderTests {

        @Test
        @DisplayName("withKeepPrefix 应设置保留前缀长度")
        void shouldSetKeepPrefix() {
            MaskContext ctx = new MaskContext().withKeepPrefix(3);
            assertEquals(3, ctx.getKeepPrefix());
        }

        @Test
        @DisplayName("withKeepSuffix 应设置保留后缀长度")
        void shouldSetKeepSuffix() {
            MaskContext ctx = new MaskContext().withKeepSuffix(4);
            assertEquals(4, ctx.getKeepSuffix());
        }

        @Test
        @DisplayName("withMaskChar 应设置遮盖字符")
        void shouldSetMaskChar() {
            MaskContext ctx = new MaskContext().withMaskChar('#');
            assertEquals('#', ctx.getMaskChar());
        }

        @Test
        @DisplayName("链式调用应支持组合")
        void shouldSupportChaining() {
            MaskContext ctx = new MaskContext()
                    .withKeepPrefix(2)
                    .withKeepSuffix(3)
                    .withMaskChar('X');

            assertEquals(2, ctx.getKeepPrefix());
            assertEquals(3, ctx.getKeepSuffix());
            assertEquals('X', ctx.getMaskChar());
        }

        @Test
        @DisplayName("withPattern 应设置正则表达式")
        void shouldSetPattern() {
            MaskContext ctx = new MaskContext().withPattern("\\d{4}");
            assertEquals("\\d{4}", ctx.getPattern());
        }

        @Test
        @DisplayName("withReplacement 应设置替换字符串")
        void shouldSetReplacement() {
            MaskContext ctx = new MaskContext().withReplacement("****");
            assertEquals("****", ctx.getReplacement());
        }
    }

    @Nested
    @DisplayName("setter 方法")
    class SetterTests {

        @Test
        @DisplayName("setKeepPrefix 应正确设置")
        void shouldSetKeepPrefixViaSetter() {
            MaskContext ctx = new MaskContext();
            ctx.setKeepPrefix(5);
            assertEquals(5, ctx.getKeepPrefix());
        }

        @Test
        @DisplayName("setMaskChar 应正确设置")
        void shouldSetMaskCharViaSetter() {
            MaskContext ctx = new MaskContext();
            ctx.setMaskChar('#');
            assertEquals('#', ctx.getMaskChar());
        }
    }

    @Nested
    @DisplayName("from 静态工厂方法")
    class FromAnnotationTests {

        @Test
        @DisplayName("from annotation 应提取注解参数")
        void shouldCreateFromAnnotation() throws Exception {
            java.lang.reflect.Field field = TestAnnotated.class.getDeclaredField("name");
            com.github.leyland.letool.sensitive.annotation.Sensitive annotation =
                    field.getAnnotation(com.github.leyland.letool.sensitive.annotation.Sensitive.class);

            MaskContext ctx = MaskContext.from(annotation);

            assertEquals(annotation.keepPrefix(), ctx.getKeepPrefix());
            assertEquals(annotation.keepSuffix(), ctx.getKeepSuffix());
            assertEquals(annotation.maskChar(), ctx.getMaskChar());
        }
    }

    // ======================== 测试用内部类 ========================

    static class TestAnnotated {
        @com.github.leyland.letool.sensitive.annotation.Sensitive(
                type = SensitiveType.PHONE,
                keepPrefix = 3,
                keepSuffix = 4,
                maskChar = '*')
        String name;
    }
}

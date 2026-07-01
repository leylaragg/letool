package com.github.leyland.letool.sensitive.strategy;

import com.github.leyland.letool.sensitive.core.MaskContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NameSensitiveStrategy 测试")
class NameSensitiveStrategyTest {

    private final NameSensitiveStrategy strategy = new NameSensitiveStrategy();

    @Nested
    @DisplayName("单姓姓名脱敏")
    class SingleSurnameTests {

        @Test
        @DisplayName("单姓两字姓名")
        void shouldMaskTwoCharName() {
            assertEquals("张*", strategy.mask("张三", MaskContext.DEFAULT));
        }

        @Test
        @DisplayName("单姓三字姓名")
        void shouldMaskThreeCharName() {
            assertEquals("王*", strategy.mask("王小明", MaskContext.DEFAULT));
        }

        @Test
        @DisplayName("单姓四字姓名")
        void shouldMaskFourCharName() {
            assertEquals("刘*", strategy.mask("刘大明白", MaskContext.DEFAULT));
        }
    }

    @Nested
    @DisplayName("复姓姓名脱敏")
    class CompoundSurnameTests {

        @Test
        @DisplayName("欧阳姓氏")
        void shouldMaskOuyang() {
            assertEquals("欧阳*", strategy.mask("欧阳修", MaskContext.DEFAULT));
        }

        @Test
        @DisplayName("司马姓氏")
        void shouldMaskSima() {
            assertEquals("司马*", strategy.mask("司马光", MaskContext.DEFAULT));
        }

        @Test
        @DisplayName("上官姓氏")
        void shouldMaskShangguan() {
            assertEquals("上官*", strategy.mask("上官婉儿", MaskContext.DEFAULT));
        }

        @Test
        @DisplayName("诸葛姓氏")
        void shouldMaskZhuge() {
            assertEquals("诸葛*", strategy.mask("诸葛亮", MaskContext.DEFAULT));
        }

        @Test
        @DisplayName("令狐姓氏")
        void shouldMaskLinghu() {
            assertEquals("令狐*", strategy.mask("令狐冲", MaskContext.DEFAULT));
        }

        @Test
        @DisplayName("慕容姓氏")
        void shouldMaskMurong() {
            assertEquals("慕容*", strategy.mask("慕容复", MaskContext.DEFAULT));
        }
    }

    @Nested
    @DisplayName("特殊输入")
    class SpecialInputTests {

        @Test
        @DisplayName("null 输入应原样返回")
        void shouldReturnNull() {
            assertNull(strategy.mask(null, MaskContext.DEFAULT));
        }

        @Test
        @DisplayName("空字符串应原样返回")
        void shouldReturnEmpty() {
            assertEquals("", strategy.mask("", MaskContext.DEFAULT));
        }

        @Test
        @DisplayName("单字姓名应保留加星号")
        void shouldMaskSingleCharName() {
            assertEquals("王*", strategy.mask("王", MaskContext.DEFAULT));
        }

        @Test
        @DisplayName("自定义遮盖字符")
        void shouldCustomMaskChar() {
            MaskContext ctx = new MaskContext().withKeepPrefix(0).withMaskChar('#');
            assertEquals("张#", strategy.mask("张三", ctx));
        }
    }
}

package com.github.leyland.letool.data.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Condition} 与 {@link Condition.Op} 的单元测试 —— 验证查询条件的构造与操作符枚举。
 */
@DisplayName("Condition 查询条件测试")
class ConditionTest {

    // ======================== 无参构造测试 ========================

    @Nested
    @DisplayName("无参构造测试")
    class NoArgsConstructorTests {

        @Test
        @DisplayName("无参构造应创建字段均为 null 的实例")
        void noArgsConstructorShouldCreateInstanceWithNullFields() {
            Condition condition = new Condition();
            assertNull(condition.getColumn());
            assertNull(condition.getOp());
            assertNull(condition.getValue());
            assertNull(condition.getValue2());
        }
    }

    // ======================== 三参构造测试（一元操作符） ========================

    @Nested
    @DisplayName("三参构造测试（一元操作符）")
    class ThreeArgConstructorTests {

        @Test
        @DisplayName("EQ 等值条件应正确构造")
        void eqConditionShouldConstructCorrectly() {
            Condition c = new Condition("user_name", Condition.Op.EQ, "张三");
            assertEquals("user_name", c.getColumn());
            assertEquals(Condition.Op.EQ, c.getOp());
            assertEquals("张三", c.getValue());
            assertNull(c.getValue2());
        }

        @Test
        @DisplayName("LIKE 模糊条件应正确构造")
        void likeConditionShouldConstructCorrectly() {
            Condition c = new Condition("title", Condition.Op.LIKE, "%关键词%");
            assertEquals("title", c.getColumn());
            assertEquals(Condition.Op.LIKE, c.getOp());
            assertEquals("%关键词%", c.getValue());
        }

        @Test
        @DisplayName("GT 大于条件应正确构造")
        void gtConditionShouldConstructCorrectly() {
            Condition c = new Condition("age", Condition.Op.GT, 18);
            assertEquals(Condition.Op.GT, c.getOp());
        }

        @Test
        @DisplayName("IS_NULL 对null条件应正确构造")
        void isNullConditionShouldConstructCorrectly() {
            Condition c = new Condition("deleted_at", Condition.Op.IS_NULL, null);
            assertEquals("deleted_at", c.getColumn());
            assertEquals(Condition.Op.IS_NULL, c.getOp());
            assertNull(c.getValue());
        }

        @Test
        @DisplayName("IS_NOT_NULL 对not null条件应正确构造")
        void isNotNullConditionShouldConstructCorrectly() {
            Condition c = new Condition("updated_at", Condition.Op.IS_NOT_NULL, null);
            assertEquals(Condition.Op.IS_NOT_NULL, c.getOp());
        }

        @Test
        @DisplayName("IN 集合条件应正确构造")
        void inConditionShouldConstructCorrectly() {
            Condition c = new Condition("status", Condition.Op.IN, Arrays.asList(1, 2, 3));
            assertEquals(Condition.Op.IN, c.getOp());
            assertEquals(Arrays.asList(1, 2, 3), c.getValue());
        }
    }

    // ======================== 四参构造测试（BETWEEN） ========================

    @Nested
    @DisplayName("四参构造测试（BETWEEN）")
    class FourArgConstructorTests {

        @Test
        @DisplayName("BETWEEN 区间条件应正确构造")
        void betweenConditionShouldConstructCorrectly() {
            Condition c = new Condition("created_at", Condition.Op.BETWEEN, "2024-01-01", "2024-12-31");
            assertEquals("created_at", c.getColumn());
            assertEquals(Condition.Op.BETWEEN, c.getOp());
            assertEquals("2024-01-01", c.getValue());
            assertEquals("2024-12-31", c.getValue2());
        }

        @Test
        @DisplayName("BETWEEN 可接受数值类型")
        void betweenConditionShouldAcceptNumericTypes() {
            Condition c = new Condition("score", Condition.Op.BETWEEN, 60, 100);
            assertEquals(60, c.getValue());
            assertEquals(100, c.getValue2());
        }

        @Test
        @DisplayName("getValue2 在非 BETWEEN 条件时应为 null")
        void getValue2ShouldBeNullForNonBetweenCondition() {
            Condition c = new Condition("name", Condition.Op.EQ, "test");
            assertNull(c.getValue2());
        }
    }

    // ======================== Condition.Op 枚举值测试 ========================

    @Nested
    @DisplayName("Condition.Op 操作符枚举测试")
    class OpEnumTests {

        @Test
        @DisplayName("应有 13 种操作符")
        void shouldHaveThirteenOperators() {
            assertEquals(13, Condition.Op.values().length, "应有 13 种操作符");
        }

        @Test
        @DisplayName("EQ 应存在且可通过 valueOf 获取")
        void eqShouldBeAvailable() {
            assertEquals(Condition.Op.EQ, Condition.Op.valueOf("EQ"));
        }

        @Test
        @DisplayName("NE 应存在")
        void neShouldBeAvailable() {
            assertEquals(Condition.Op.NE, Condition.Op.valueOf("NE"));
        }

        @Test
        @DisplayName("GT/GE/LT/LE 比较操作符应全部存在")
        void comparisonOperatorsShouldAllExist() {
            assertNotNull(Condition.Op.valueOf("GT"));
            assertNotNull(Condition.Op.valueOf("GE"));
            assertNotNull(Condition.Op.valueOf("LT"));
            assertNotNull(Condition.Op.valueOf("LE"));
        }

        @Test
        @DisplayName("LIKE/NOT_LIKE 模糊操作符应存在")
        void likeOperatorsShouldExist() {
            assertNotNull(Condition.Op.valueOf("LIKE"));
            assertNotNull(Condition.Op.valueOf("NOT_LIKE"));
        }

        @Test
        @DisplayName("IN/NOT_IN 集合操作符应存在")
        void inOperatorsShouldExist() {
            assertNotNull(Condition.Op.valueOf("IN"));
            assertNotNull(Condition.Op.valueOf("NOT_IN"));
        }

        @Test
        @DisplayName("BETWEEN 区间操作符应存在")
        void betweenOperatorShouldExist() {
            assertNotNull(Condition.Op.valueOf("BETWEEN"));
        }

        @Test
        @DisplayName("IS_NULL/IS_NOT_NULL 空值操作符应存在")
        void nullOperatorsShouldExist() {
            assertNotNull(Condition.Op.valueOf("IS_NULL"));
            assertNotNull(Condition.Op.valueOf("IS_NOT_NULL"));
        }

        @Test
        @DisplayName("valueOf 传入不存在名称应抛出 IllegalArgumentException")
        void valueOfUnknownNameShouldThrow() {
            assertThrows(IllegalArgumentException.class, () -> Condition.Op.valueOf("UNKNOWN"));
        }
    }
}

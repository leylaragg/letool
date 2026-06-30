package com.github.leyland.letool.data.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Sort}, {@link Sort.Order}, {@link Sort.Direction} 的单元测试 —— 验证排序构建器、排序项与排序方向。
 */
@DisplayName("Sort 排序测试")
class SortTest {

    // ======================== Sort 构建器测试 ========================

    @Nested
    @DisplayName("Sort 排序构建器测试")
    class SortBuilderTests {

        @Test
        @DisplayName("新建 Sort 应为空")
        void newSortShouldBeEmpty() {
            Sort sort = new Sort();
            assertTrue(sort.isEmpty(), "新建的 Sort 应为空");
            assertTrue(sort.getOrders().isEmpty(), "排序列表应为空");
        }

        @Test
        @DisplayName("asc() 应添加升序排序项并返回自身")
        void ascShouldAddAscendingOrderAndReturnSelf() {
            Sort sort = new Sort();
            Sort result = sort.asc("user_name");
            assertSame(sort, result, "asc() 应返回 Sort 自身以支持链式调用");
            assertFalse(sort.isEmpty());
            assertEquals(1, sort.getOrders().size());
            assertEquals("user_name", sort.getOrders().get(0).getColumn());
            assertEquals(Sort.Direction.ASC, sort.getOrders().get(0).getDirection());
        }

        @Test
        @DisplayName("desc() 应添加降序排序项并返回自身")
        void descShouldAddDescendingOrderAndReturnSelf() {
            Sort sort = new Sort();
            Sort result = sort.desc("created_at");
            assertSame(sort, result, "desc() 应返回 Sort 自身以支持链式调用");
            assertFalse(sort.isEmpty());
            assertEquals(1, sort.getOrders().size());
            assertEquals("created_at", sort.getOrders().get(0).getColumn());
            assertEquals(Sort.Direction.DESC, sort.getOrders().get(0).getDirection());
        }

        @Test
        @DisplayName("链式调用应支持多字段排序")
        void chainedCallsShouldSupportMultiFieldSort() {
            Sort sort = new Sort()
                    .asc("age")
                    .desc("name")
                    .asc("id");

            assertEquals(3, sort.getOrders().size());
            assertEquals(Sort.Direction.ASC, sort.getOrders().get(0).getDirection());
            assertEquals(Sort.Direction.DESC, sort.getOrders().get(1).getDirection());
            assertEquals(Sort.Direction.ASC, sort.getOrders().get(2).getDirection());

            assertEquals("age", sort.getOrders().get(0).getColumn());
            assertEquals("name", sort.getOrders().get(1).getColumn());
            assertEquals("id", sort.getOrders().get(2).getColumn());
        }

        @Test
        @DisplayName("重复调用 asc/desc 应累积排序项")
        void repeatedCallsShouldAccumulateOrders() {
            Sort sort = new Sort();
            sort.asc("a");
            sort.asc("b");
            sort.asc("c");
            assertEquals(3, sort.getOrders().size());
        }
    }

    // ======================== Sort.Direction 枚举测试 ========================

    @Nested
    @DisplayName("Sort.Direction 排序方向枚举测试")
    class DirectionEnumTests {

        @Test
        @DisplayName("应有 ASC 和 DESC 两个值")
        void shouldHaveTwoValues() {
            assertEquals(2, Sort.Direction.values().length);
        }

        @Test
        @DisplayName("ASC 应可通过 valueOf 获取")
        void ascShouldBeAvailable() {
            assertEquals(Sort.Direction.ASC, Sort.Direction.valueOf("ASC"));
        }

        @Test
        @DisplayName("DESC 应可通过 valueOf 获取")
        void descShouldBeAvailable() {
            assertEquals(Sort.Direction.DESC, Sort.Direction.valueOf("DESC"));
        }

        @Test
        @DisplayName("ASC 和 DESC 的 name() 分别为 'ASC' 和 'DESC'")
        void namesShouldBeUpperCase() {
            assertEquals("ASC", Sort.Direction.ASC.name());
            assertEquals("DESC", Sort.Direction.DESC.name());
        }
    }

    // ======================== Sort.Order 测试 ========================

    @Nested
    @DisplayName("Sort.Order 排序项测试")
    class OrderTests {

        @Test
        @DisplayName("无参构造应创建字段为 null 的实例")
        void noArgsConstructorShouldCreateEmptyInstance() {
            Sort.Order order = new Sort.Order();
            assertNull(order.getColumn());
            assertNull(order.getDirection());
        }

        @Test
        @DisplayName("带参构造应正确设置列名和方向")
        void parameterizedConstructorShouldSetColumnAndDirection() {
            Sort.Order order = new Sort.Order("email", Sort.Direction.ASC);
            assertEquals("email", order.getColumn());
            assertEquals(Sort.Direction.ASC, order.getDirection());
        }

        @Test
        @DisplayName("getColumn 和 getDirection 应返回设置的值")
        void gettersShouldReturnSetValues() {
            Sort.Order order = new Sort.Order("score", Sort.Direction.DESC);
            assertEquals("score", order.getColumn());
            assertEquals(Sort.Direction.DESC, order.getDirection());
        }
    }

    // ======================== 边界场景测试 ========================

    @Nested
    @DisplayName("边界场景测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("asc 传入 null 列名应可构造")
        void ascWithNullColumnShouldConstruct() {
            Sort sort = new Sort().asc(null);
            assertNull(sort.getOrders().get(0).getColumn());
        }

        @Test
        @DisplayName("desc 传入空字符串应可构造")
        void descWithEmptyStringShouldConstruct() {
            Sort sort = new Sort().desc("");
            assertEquals("", sort.getOrders().get(0).getColumn());
        }

        @Test
        @DisplayName("getOrders 返回的列表应为可读引用（允许外部修改）")
        void getOrdersShouldReturnDirectReference() {
            Sort sort = new Sort().asc("id");
            sort.getOrders().clear();
            assertTrue(sort.isEmpty(), "直接清空 getOrders 返回的列表会影响 Sort 内部状态");
        }
    }
}

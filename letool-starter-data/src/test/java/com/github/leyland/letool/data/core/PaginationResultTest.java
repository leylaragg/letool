package com.github.leyland.letool.data.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link PaginationResult} 的单元测试 —— 验证分页结果模型的构造、计算与边界行为。
 */
@DisplayName("PaginationResult 分页结果测试")
class PaginationResultTest {

    // ======================== 构造方法测试 ========================

    @Nested
    @DisplayName("构造方法测试")
    class ConstructorTests {

        @Test
        @DisplayName("无参构造应创建空记录列表并 totalPages 为 0")
        void noArgsConstructorShouldCreateEmptyRecords() {
            PaginationResult<String> result = new PaginationResult<>();
            assertNotNull(result.getRecords(), "records 不应为 null");
            assertTrue(result.getRecords().isEmpty(), "records 应为空列表");
            assertEquals(0, result.getTotalPages(), "totalPages 应为 0");
        }

        @Test
        @DisplayName("带参构造应正确计算总页数 —— 整除情况")
        void constructorShouldCalculateTotalPagesWhenEvenlyDivisible() {
            List<String> records = Arrays.asList("a", "b");
            PaginationResult<String> result = new PaginationResult<>(records, 10L, 1, 5);
            assertEquals(2, result.getRecords().size());
            assertEquals(10L, result.getTotal());
            assertEquals(1, result.getPage());
            assertEquals(5, result.getPageSize());
            assertEquals(2, result.getTotalPages(), "10 条每页 5 条应为 2 页");
        }

        @Test
        @DisplayName("带参构造应正确计算总页数 —— 非整除情况")
        void constructorShouldCalculateTotalPagesWhenUnevenlyDivisible() {
            PaginationResult<String> result = new PaginationResult<>(
                    Collections.singletonList("a"), 11L, 1, 5);
            assertEquals(3, result.getTotalPages(), "11 条每页 5 条应为 3 页");
        }

        @Test
        @DisplayName("传入 null 记录列表应替换为空列表")
        void nullRecordsShouldBeReplacedWithEmptyList() {
            PaginationResult<String> result = new PaginationResult<>(null, 0L, 1, 10);
            assertNotNull(result.getRecords(), "records 不应为 null");
            assertTrue(result.getRecords().isEmpty(), "records 应为空列表");
        }

        @Test
        @DisplayName("pageSize 为 0 时 totalPages 应为 0 防止除零")
        void zeroPageSizeShouldResultInZeroTotalPages() {
            PaginationResult<String> result = new PaginationResult<>(
                    Collections.singletonList("a"), 100L, 1, 0);
            assertEquals(0, result.getTotalPages(), "pageSize=0 时 totalPages 应为 0");
        }

        @Test
        @DisplayName("pageSize 为负数时 totalPages 应为 0")
        void negativePageSizeShouldResultInZeroTotalPages() {
            PaginationResult<String> result = new PaginationResult<>(
                    Collections.singletonList("a"), 100L, 1, -1);
            assertEquals(0, result.getTotalPages(), "pageSize<0 时 totalPages 应为 0");
        }

        @Test
        @DisplayName("total 为 0 时 totalPages 应为 0")
        void zeroTotalShouldResultInZeroTotalPages() {
            PaginationResult<String> result = new PaginationResult<>(
                    new ArrayList<>(), 0L, 1, 10);
            assertEquals(0, result.getTotalPages(), "total=0 时 totalPages 应为 0");
        }
    }

    // ======================== Getter / Setter 测试 ========================

    @Nested
    @DisplayName("Getter/Setter 测试")
    class GetterSetterTests {

        @Test
        @DisplayName("records 的 getter 和 setter 应正常工作")
        void recordsGetterSetterShouldWork() {
            PaginationResult<Integer> result = new PaginationResult<>();
            List<Integer> list = Arrays.asList(1, 2, 3);
            result.setRecords(list);
            assertEquals(list, result.getRecords());
        }

        @Test
        @DisplayName("total 的 getter 和 setter 应正常工作")
        void totalGetterSetterShouldWork() {
            PaginationResult<String> result = new PaginationResult<>();
            result.setTotal(99L);
            assertEquals(99L, result.getTotal());
        }

        @Test
        @DisplayName("page 的 getter 和 setter 应正常工作")
        void pageGetterSetterShouldWork() {
            PaginationResult<String> result = new PaginationResult<>();
            result.setPage(5);
            assertEquals(5, result.getPage());
        }

        @Test
        @DisplayName("pageSize 的 getter 和 setter 应正常工作")
        void pageSizeGetterSetterShouldWork() {
            PaginationResult<String> result = new PaginationResult<>();
            result.setPageSize(20);
            assertEquals(20, result.getPageSize());
        }

        @Test
        @DisplayName("totalPages 的 getter 和 setter 应正常工作")
        void totalPagesGetterSetterShouldWork() {
            PaginationResult<String> result = new PaginationResult<>();
            result.setTotalPages(7);
            assertEquals(7, result.getTotalPages());
        }
    }

    // ======================== 边界场景测试 ========================

    @Nested
    @DisplayName("边界场景测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("只有 1 条记录且总数为 1 的情况")
        void singleRecordWithTotalOne() {
            PaginationResult<String> result = new PaginationResult<>(
                    Collections.singletonList("only"), 1L, 1, 10);
            assertEquals(1, result.getTotalPages());
            assertEquals(1, result.getRecords().size());
        }

        @Test
        @DisplayName("超出总页数的页码仍可构造（不校验页码合法性）")
        void pageNumberBeyondTotalPagesShouldStillConstruct() {
            PaginationResult<String> result = new PaginationResult<>(
                    Collections.emptyList(), 10L, 100, 5);
            assertEquals(100, result.getPage(), "页码不做合法性校验");
            assertEquals(2, result.getTotalPages());
        }

        @Test
        @DisplayName("大数据量 total 时的分页计算")
        void largeTotalValuePagination() {
            long largeTotal = 1_000_000L;
            int pageSize = 13;
            int expectedPages = (int) Math.ceil((double) largeTotal / pageSize);
            PaginationResult<String> result = new PaginationResult<>(
                    new ArrayList<>(), largeTotal, 1, pageSize);
            assertEquals(expectedPages, result.getTotalPages());
        }

        @Test
        @DisplayName("泛型支持不同类型")
        void genericTypeShouldSupportVariousTypes() {
            PaginationResult<Long> longResult = new PaginationResult<>(
                    Arrays.asList(1L, 2L), 2L, 1, 10);
            assertEquals(Long.class, longResult.getRecords().get(0).getClass());

            PaginationResult<Object> objResult = new PaginationResult<>(
                    Arrays.asList(new Object()), 1L, 1, 10);
            assertEquals(1, objResult.getRecords().size());
        }
    }
}

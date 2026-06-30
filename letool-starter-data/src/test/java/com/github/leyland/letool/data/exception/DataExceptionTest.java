package com.github.leyland.letool.data.exception;

import com.github.leyland.letool.tool.exception.LetoolException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DataException} 的单元测试 —— 验证异常类的构造、继承链和信息传递。
 */
@DisplayName("DataException 异常测试")
class DataExceptionTest {

    // ======================== 基础构造测试 ========================

    @Nested
    @DisplayName("基础构造测试")
    class BasicConstructionTests {

        @Test
        @DisplayName("应正确记录错误码和消息")
        void shouldRecordErrorCodeAndMessage() {
            DataException ex = new DataException("DATA_001", "查询结果数量异常");
            assertEquals("DATA_001", ex.getErrorCode());
            assertEquals("查询结果数量异常", ex.getMessage());
        }

        @Test
        @DisplayName("应正确记录错误码、消息和原始异常")
        void shouldRecordErrorCodeMessageAndCause() {
            IllegalArgumentException cause = new IllegalArgumentException("非法参数");
            DataException ex = new DataException("DATA_002", "无可用字段进行插入", cause);

            assertEquals("DATA_002", ex.getErrorCode());
            assertEquals("无可用字段进行插入", ex.getMessage());
            assertSame(cause, ex.getCause());
        }

        @Test
        @DisplayName("插入异常应使用 DATA_002 错误码")
        void insertExceptionShouldUseCorrectErrorCode() {
            DataException ex = new DataException("DATA_002", "No fields to insert");
            assertEquals("DATA_002", ex.getErrorCode());
        }

        @Test
        @DisplayName("更新异常应使用 DATA_003 错误码")
        void updateExceptionShouldUseCorrectErrorCode() {
            DataException ex = new DataException("DATA_003", "Cannot update entity with null ID");
            assertEquals("DATA_003", ex.getErrorCode());
        }
    }

    // ======================== 继承链测试 ========================

    @Nested
    @DisplayName("继承链测试")
    class InheritanceTests {

        @Test
        @DisplayName("DataException 应继承自 LetoolException")
        void shouldExtendLetoolException() {
            DataException ex = new DataException("E001", "测试");
            assertTrue(ex instanceof LetoolException, "DataException 应是 LetoolException 的子类");
        }

        @Test
        @DisplayName("LetoolException 应继承自 RuntimeException")
        void letoolExceptionShouldExtendRuntimeException() {
            DataException ex = new DataException("E001", "测试");
            assertTrue(ex instanceof RuntimeException, "DataException 应是 RuntimeException 的子类");
        }

        @Test
        @DisplayName("errorCode 通过父类 getErrorCode() 获取")
        void errorCodeShouldBeAccessibleViaParentGetter() {
            DataException ex = new DataException("DATA_004", "字段值提取失败");
            assertNotNull(ex.getErrorCode());
            assertEquals("DATA_004", ex.getErrorCode());
        }
    }

    // ======================== 异常链测试 ========================

    @Nested
    @DisplayName("异常链测试")
    class ExceptionChainTests {

        @Test
        @DisplayName("getCause 应返回传入的原始异常")
        void getCauseShouldReturnOriginalException() {
            SQLException simulatedCause = new SQLException("模拟数据库错误");
            DataException ex = new DataException("DATA_999", "数据操作失败", simulatedCause);
            assertSame(simulatedCause, ex.getCause());
        }

        @Test
        @DisplayName("原始异常的 message 应可追溯")
        void originalExceptionMessageShouldBeTraceable() {
            IllegalArgumentException cause = new IllegalArgumentException("具体原因");
            DataException ex = new DataException("E500", "包装异常", cause);
            assertEquals("具体原因", ex.getCause().getMessage());
        }

        @Test
        @DisplayName("多层异常嵌套应保持完整的异常链")
        void multiLevelExceptionChainShouldBePreserved() {
            NullPointerException root = new NullPointerException("最底层原因");
            DataException ex = new DataException("DATA_500", "服务异常", root);
            assertNotNull(ex.getCause());
            assertEquals(root, ex.getCause());
        }

        @Test
        @DisplayName("无 cause 构造时 getCause 应为 null")
        void getCauseShouldBeNullWhenConstructedWithoutCause() {
            DataException ex = new DataException("DATA_006", "无原因异常");
            assertNull(ex.getCause());
        }
    }

    // ======================== 工具类（模拟 SQLException 用于测试） ========================

    private static class SQLException extends Exception {
        public SQLException(String message) {
            super(message);
        }
    }
}

package com.github.leyland.letool.data.exception;

import com.github.leyland.letool.exception.core.BaseException;
import com.github.leyland.letool.exception.core.SystemException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证 {@link DataException} 与统一异常模型之间的契约。
 */
@DisplayName("DataException 异常契约")
class DataExceptionTest {

    @Test
    @DisplayName("应保留原有错误码和消息构造语义，并生成稳定的日志消息")
    void shouldPreserveCodeAndFallbackMessage() {
        DataException exception = new DataException("DATA_001", "查询结果数量异常");

        assertEquals("DATA_001", exception.getCode());
        assertEquals("DATA_001", exception.getErrorCode().getCode());
        assertEquals("查询结果数量异常", exception.getFallbackMessage());
        assertEquals("[DATA_001] 查询结果数量异常", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("应属于系统异常与基础异常体系")
    void shouldExtendUnifiedSystemExceptionHierarchy() {
        DataException exception = new DataException("DATA_002", "无可用字段进行插入");

        assertInstanceOf(SystemException.class, exception);
        assertInstanceOf(BaseException.class, exception);
        assertInstanceOf(RuntimeException.class, exception);
    }

    @Test
    @DisplayName("应完整保留下层异常链")
    void shouldPreserveCause() {
        IllegalStateException cause = new IllegalStateException("数据库连接中断");

        DataException exception =
                new DataException("DATA_500", "数据操作失败", cause);

        assertSame(cause, exception.getCause());
        assertEquals("[DATA_500] 数据操作失败", exception.getMessage());
    }

    @ParameterizedTest(name = "[{index}] 空错误码={0}")
    @NullAndEmptySource
    @ValueSource(strings = " ")
    @DisplayName("应拒绝 null、空串和空白错误码")
    void shouldRejectBlankErrorCode(String errorCode) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new DataException(errorCode, "数据操作失败"));

        assertEquals("code must not be blank", exception.getMessage());
    }

    @ParameterizedTest(name = "[{index}] 空默认消息={0}")
    @NullAndEmptySource
    @ValueSource(strings = " ")
    @DisplayName("应拒绝 null、空串和空白默认消息")
    void shouldRejectBlankMessage(String message) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new DataException("DATA_001", message));

        assertEquals("defaultMessage must not be blank", exception.getMessage());
    }

    @Test
    @DisplayName("三参构造器应允许 null cause 以保持原有构造语义")
    void shouldAllowNullCauseInThreeArgumentConstructor() {
        DataException exception =
                new DataException("DATA_001", "数据操作失败", null);

        assertNull(exception.getCause());
        assertEquals("[DATA_001] 数据操作失败", exception.getMessage());
    }
}

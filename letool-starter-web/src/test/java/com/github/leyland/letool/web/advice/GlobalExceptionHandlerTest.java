package com.github.leyland.letool.web.advice;

import com.github.leyland.letool.tool.exception.BusinessException;
import com.github.leyland.letool.tool.exception.LetoolException;
import com.github.leyland.letool.tool.exception.SystemException;
import com.github.leyland.letool.tool.model.R;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("全局异常处理器测试")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Nested
    @DisplayName("BusinessException")
    class BusinessExceptionTests {

        @Test
        @DisplayName("返回 400 状态码和错误信息")
        void shouldReturnBadRequest() {
            BusinessException ex = new BusinessException("B001", "业务异常");

            R<Void> result = handler.handleBusinessException(ex);

            assertEquals("B001", result.getCode());
            assertTrue(result.getMessage().contains("业务异常"));
        }
    }

    @Nested
    @DisplayName("SystemException")
    class SystemExceptionTests {

        @Test
        @DisplayName("返回错误码和消息")
        void shouldReturnErrorCode() {
            SystemException ex = new SystemException("SYS_001", "系统错误");

            R<Void> result = handler.handleSystemException(ex);

            assertEquals("SYS_001", result.getCode());
            assertTrue(result.getMessage().contains("系统错误"));
        }
    }

    @Nested
    @DisplayName("LetoolException")
    class LetoolExceptionTests {

        @Test
        @DisplayName("返回错误码和消息")
        void shouldReturnErrorCode() {
            LetoolException ex = new LetoolException("E001", "工具异常") {};

            R<Void> result = handler.handleLetoolException(ex);

            assertEquals("E001", result.getCode());
        }
    }

    @Nested
    @DisplayName("IllegalArgumentException")
    class IllegalArgumentExceptionTests {

        @Test
        @DisplayName("参数异常返回 400")
        void shouldReturnBadRequest() {
            IllegalArgumentException ex = new IllegalArgumentException("参数不合法");

            R<Void> result = handler.handleIllegalArgumentException(ex);

            assertEquals("ARG_001", result.getCode());
            assertEquals("参数不合法", result.getMessage());
        }
    }

    @Nested
    @DisplayName("Exception 兜底")
    class GenericExceptionTests {

        @Test
        @DisplayName("未知异常返回通用错误信息")
        void shouldReturnGenericError() {
            Exception ex = new Exception("未知错误");

            R<Void> result = handler.handleGenericException(ex);

            assertEquals("SYS_001", result.getCode());
            assertEquals("系统内部错误，请稍后重试", result.getMessage());
        }
    }
}

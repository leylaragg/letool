package com.github.leyland.letool.sms.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SmsResult 短信发送结果模型测试")
class SmsResultTest {

    @Nested
    @DisplayName("success() 工厂方法测试")
    class SuccessFactoryTests {

        @Test
        @DisplayName("应创建成功结果")
        void shouldCreateSuccessResult() {
            SmsResult result = SmsResult.success("REQ-12345");

            assertTrue(result.isSuccess());
            assertEquals("REQ-12345", result.getRequestId());
            assertNull(result.getErrorCode());
            assertNull(result.getErrorMessage());
        }
    }

    @Nested
    @DisplayName("fail() 工厂方法测试")
    class FailFactoryTests {

        @Test
        @DisplayName("应创建失败结果")
        void shouldCreateFailResult() {
            SmsResult result = SmsResult.fail("E001", "模板不存在");

            assertFalse(result.isSuccess());
            assertEquals("E001", result.getErrorCode());
            assertEquals("模板不存在", result.getErrorMessage());
            assertNull(result.getRequestId());
        }
    }

    @Nested
    @DisplayName("字段互斥性测试")
    class FieldExclusivityTests {

        @Test
        @DisplayName("成功时 errorCode 和 errorMessage 应为 null")
        void successShouldHaveNullErrors() {
            SmsResult result = SmsResult.success("REQ-001");
            assertNull(result.getErrorCode());
            assertNull(result.getErrorMessage());
        }

        @Test
        @DisplayName("失败时 requestId 应为 null")
        void failShouldHaveNullRequestId() {
            SmsResult result = SmsResult.fail("ERR", "失败");
            assertNull(result.getRequestId());
        }
    }

    @Nested
    @DisplayName("toString() 测试")
    class ToStringTests {

        @Test
        @DisplayName("成功结果的 toString 应包含 requestId")
        void successToStringShouldContainRequestId() {
            SmsResult result = SmsResult.success("REQ-999");
            assertTrue(result.toString().contains("success=true"));
            assertTrue(result.toString().contains("REQ-999"));
        }

        @Test
        @DisplayName("失败结果的 toString 应包含错误信息")
        void failToStringShouldContainErrors() {
            SmsResult result = SmsResult.fail("E002", "超限");
            assertTrue(result.toString().contains("success=false"));
            assertTrue(result.toString().contains("E002"));
            assertTrue(result.toString().contains("超限"));
        }
    }

    @Nested
    @DisplayName("不可变性测试")
    class ImmutabilityTests {

        @Test
        @DisplayName("工厂方法应返回不同实例")
        void factoriesShouldReturnDifferentInstances() {
            SmsResult r1 = SmsResult.success("a");
            SmsResult r2 = SmsResult.success("a");
            assertNotSame(r1, r2);
        }
    }
}

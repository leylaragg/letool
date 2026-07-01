package com.github.leyland.letool.rule.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RuleException 测试")
class RuleExceptionTest {

    @Nested
    @DisplayName("构造方法")
    class ConstructorTests {

        @Test
        @DisplayName("双参数构造应设置 errorCode 和 message")
        void shouldSetErrorCodeAndMessage() {
            RuleException ex = new RuleException("PARSE_001", "YAML 格式错误");

            assertEquals("PARSE_001", ex.getErrorCode());
            assertEquals("YAML 格式错误", ex.getMessage());
            assertNull(ex.getChainName());
        }

        @Test
        @DisplayName("三参数构造应设置 errorCode、message 和 cause")
        void shouldSetErrorCodeMessageAndCause() {
            RuntimeException cause = new RuntimeException("原始异常");
            RuleException ex = new RuleException("EXEC_001", "执行失败", cause);

            assertEquals("EXEC_001", ex.getErrorCode());
            assertEquals("执行失败", ex.getMessage());
            assertSame(cause, ex.getCause());
            assertNull(ex.getChainName());
        }

        @Test
        @DisplayName("四参数构造应设置 errorCode、message、chainName 和 cause")
        void shouldSetAllFields() {
            RuntimeException cause = new RuntimeException("原始异常");
            RuleException ex = new RuleException("EXEC_NODE", "节点执行失败", "riskChain", cause);

            assertEquals("EXEC_NODE", ex.getErrorCode());
            assertEquals("节点执行失败", ex.getMessage());
            assertEquals("riskChain", ex.getChainName());
            assertSame(cause, ex.getCause());
        }
    }

    @Nested
    @DisplayName("继承关系")
    class InheritanceTests {

        @Test
        @DisplayName("RuleException 应为 RuntimeException 的子类")
        void shouldBeRuntimeException() {
            RuleException ex = new RuleException("ERR", "msg");
            assertTrue(ex instanceof RuntimeException);
        }
    }

    @Nested
    @DisplayName("错误码常量")
    class ErrorCodeTests {

        @Test
        @DisplayName("errorCode 永不为 null")
        void errorCodeShouldNeverBeNull() {
            RuleException ex = new RuleException("TEST_001", "test");
            assertNotNull(ex.getErrorCode());
        }
    }
}

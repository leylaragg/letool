package com.github.leyland.letool.lock.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link LockException} 的单元测试 —— 验证分布式锁异常的构造、继承链和信息传递。
 */
@DisplayName("LockException 异常测试")
class LockExceptionTest {

    // ======================== 基础构造测试 ========================

    @Nested
    @DisplayName("基础构造测试")
    class BasicConstructionTests {

        @Test
        @DisplayName("单参构造应正确记录错误消息")
        void singleArgConstructorShouldRecordMessage() {
            LockException ex = new LockException("获取锁超时: order:123");
            assertEquals("获取锁超时: order:123", ex.getMessage());
            assertNull(ex.getCause(), "单参构造的 cause 应为 null");
        }

        @Test
        @DisplayName("双参构造应正确记录错误消息和原始异常")
        void twoArgConstructorShouldRecordMessageAndCause() {
            InterruptedException cause = new InterruptedException("线程中断");
            LockException ex = new LockException("获取锁被中断: order:456", cause);

            assertEquals("获取锁被中断: order:456", ex.getMessage());
            assertSame(cause, ex.getCause());
        }

        @Test
        @DisplayName("获取锁失败消息应包含 lock key")
        void failedToAcquireLockMessageShouldContainLockKey() {
            LockException ex = new LockException("Failed to acquire lock: user:789");
            assertTrue(ex.getMessage().contains("user:789"),
                    "异常消息应包含失败的 lock key");
            assertTrue(ex.getMessage().contains("Failed to acquire lock"),
                    "异常消息应说明失败原因");
        }
    }

    // ======================== 继承链测试 ========================

    @Nested
    @DisplayName("继承链测试")
    class InheritanceTests {

        @Test
        @DisplayName("LockException 应继承自 RuntimeException")
        void shouldExtendRuntimeException() {
            LockException ex = new LockException("测试");
            assertTrue(ex instanceof RuntimeException,
                    "LockException 应是 RuntimeException 的子类");
        }

        @Test
        @DisplayName("LockException 应可被 try-catch 捕获为 RuntimeException")
        void shouldBeCatchableAsRuntimeException() {
            try {
                throw new LockException("测试异常");
            } catch (RuntimeException e) {
                assertTrue(e instanceof LockException);
                assertEquals("测试异常", e.getMessage());
            }
        }
    }

    // ======================== 与 LockException 对应的 DataException 层级对比 ========================

    @Nested
    @DisplayName("异常类型区分测试")
    class ExceptionTypeDistinctionTests {

        @Test
        @DisplayName("LockException 与 DataException 是独立类型（均为 RuntimeException 子类）")
        void lockExceptionAndDataExceptionAreIndependentTypes() {
            LockException lockEx = new LockException("锁异常");
            // DataException 继承自 LetoolException（非本模块），这里仅验证 LockException 本身
            assertTrue(lockEx instanceof LockException);
            assertTrue(lockEx instanceof RuntimeException);
        }
    }

    // ======================== 异常链测试 ========================

    @Nested
    @DisplayName("异常链测试")
    class ExceptionChainTests {

        @Test
        @DisplayName("getCause 应返回传入的原始异常")
        void getCauseShouldReturnOriginalException() {
            RuntimeException orig = new RuntimeException("底层错误");
            LockException ex = new LockException("包装信息", orig);
            assertSame(orig, ex.getCause());
        }

        @Test
        @DisplayName("多层嵌套应保持完整的异常链")
        void multiLevelChainShouldBePreserved() {
            NullPointerException root = new NullPointerException("根因");
            LockException ex = new LockException("释放锁失败", root);
            assertNotNull(ex.getCause());
            assertEquals(root, ex.getCause());
            assertEquals("根因", ex.getCause().getMessage());
        }

        @Test
        @DisplayName("无 cause 构造时 getCause 应为 null")
        void getCauseShouldBeNullWhenNoCause() {
            LockException ex = new LockException("仅消息");
            assertNull(ex.getCause());
        }

        @Test
        @DisplayName("消息为 null 时可正常构造")
        void nullMessageShouldBeAllowed() {
            LockException ex = new LockException(null);
            assertNull(ex.getMessage());
        }

        @Test
        @DisplayName("消息为 null 且带 cause 时可正常构造")
        void nullMessageWithCauseShouldBeAllowed() {
            Exception cause = new Exception("cause");
            LockException ex = new LockException(null, cause);
            assertNull(ex.getMessage());
            assertSame(cause, ex.getCause());
        }
    }
}

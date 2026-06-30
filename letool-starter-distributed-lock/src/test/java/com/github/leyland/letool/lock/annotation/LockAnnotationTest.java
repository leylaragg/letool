package com.github.leyland.letool.lock.annotation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Lock} 与 {@link Idempotent} 注解的单元测试 —— 验证注解的元信息、默认值和标注行为。
 */
@DisplayName("分布式锁注解测试")
class LockAnnotationTest {

    // ======================== 辅助类 ========================

    static class TestService {
        @Lock(key = "order:#{#orderId}", waitTime = 5, leaseTime = 60, timeUnit = TimeUnit.SECONDS)
        public void processOrder(Long orderId) {}

        @Lock(key = "user:#{#userId}")
        public void defaultLockMethod(Long userId) {}

        @Idempotent(key = "pay:#{#orderId}", ttl = 3600)
        public String pay(Long orderId) { return "success"; }

        @Idempotent(key = "default:#{#id}")
        public void defaultIdempotentMethod(Long id) {}
    }

    // ======================== @Lock 注解基础测试 ========================

    @Nested
    @DisplayName("@Lock 注解基础测试")
    class LockAnnotationBasicTests {

        @Test
        @DisplayName("应存在于方法级别")
        void shouldBeAppliedToMethod() throws NoSuchMethodException {
            Method method = TestService.class.getMethod("processOrder", Long.class);
            Lock lock = method.getAnnotation(Lock.class);
            assertNotNull(lock, "@Lock 注解应存在于方法上");
        }

        @Test
        @DisplayName("Target 应为 METHOD")
        void targetShouldBeMethod() {
            java.lang.annotation.Target target = Lock.class.getAnnotation(java.lang.annotation.Target.class);
            assertNotNull(target);
            boolean hasMethod = false;
            for (java.lang.annotation.ElementType et : target.value()) {
                if (et == java.lang.annotation.ElementType.METHOD) hasMethod = true;
            }
            assertTrue(hasMethod, "@Lock 的 @Target 应包含 METHOD");
        }

        @Test
        @DisplayName("Retention 应为 RUNTIME")
        void retentionShouldBeRuntime() {
            java.lang.annotation.Retention retention = Lock.class.getAnnotation(java.lang.annotation.Retention.class);
            assertNotNull(retention);
            assertEquals(java.lang.annotation.RetentionPolicy.RUNTIME, retention.value(),
                    "@Lock 的 @Retention 应为 RUNTIME");
        }

        @Test
        @DisplayName("应有 @Documented 元注解")
        void shouldHaveDocumentedMetaAnnotation() {
            Documented doc = Lock.class.getAnnotation(Documented.class);
            assertNotNull(doc, "@Lock 应有 @Documented 注解");
        }
    }

    // ======================== @Lock 属性默认值测试 ========================

    @Nested
    @DisplayName("@Lock 属性默认值测试")
    class LockDefaultValuesTests {

        @Test
        @DisplayName("waitTime 默认应为 3")
        void waitTimeDefaultShouldBe3() throws NoSuchMethodException {
            Method method = TestService.class.getMethod("defaultLockMethod", Long.class);
            Lock lock = method.getAnnotation(Lock.class);
            assertEquals(3L, lock.waitTime(), "waitTime 默认值应为 3");
        }

        @Test
        @DisplayName("leaseTime 默认应为 30")
        void leaseTimeDefaultShouldBe30() throws NoSuchMethodException {
            Method method = TestService.class.getMethod("defaultLockMethod", Long.class);
            Lock lock = method.getAnnotation(Lock.class);
            assertEquals(30L, lock.leaseTime(), "leaseTime 默认值应为 30");
        }

        @Test
        @DisplayName("timeUnit 默认应为 TimeUnit.SECONDS")
        void timeUnitDefaultShouldBeSeconds() throws NoSuchMethodException {
            Method method = TestService.class.getMethod("defaultLockMethod", Long.class);
            Lock lock = method.getAnnotation(Lock.class);
            assertEquals(TimeUnit.SECONDS, lock.timeUnit(), "timeUnit 默认值应为 SECONDS");
        }

        @Test
        @DisplayName("key 无默认值，必须由用户显式指定")
        void keyHasNoDefaultValue() throws NoSuchMethodException {
            Method method = TestService.class.getMethod("defaultLockMethod", Long.class);
            Lock lock = method.getAnnotation(Lock.class);
            assertNotNull(lock.key(), "key 应有值");
            assertFalse(lock.key().isEmpty(), "key 不应为空");
        }
    }

    // ======================== @Lock 自定义值测试 ========================

    @Nested
    @DisplayName("@Lock 自定义值测试")
    class LockCustomValuesTests {

        @Test
        @DisplayName("自定义 waitTime=5 应被正确读取")
        void customWaitTimeShouldBeReadCorrectly() throws NoSuchMethodException {
            Method method = TestService.class.getMethod("processOrder", Long.class);
            Lock lock = method.getAnnotation(Lock.class);
            assertEquals(5L, lock.waitTime());
        }

        @Test
        @DisplayName("自定义 leaseTime=60 应被正确读取")
        void customLeaseTimeShouldBeReadCorrectly() throws NoSuchMethodException {
            Method method = TestService.class.getMethod("processOrder", Long.class);
            Lock lock = method.getAnnotation(Lock.class);
            assertEquals(60L, lock.leaseTime());
        }

        @Test
        @DisplayName("SpEL key 表达式应被完整保留")
        void spELKeyShouldBePreserved() throws NoSuchMethodException {
            Method method = TestService.class.getMethod("processOrder", Long.class);
            Lock lock = method.getAnnotation(Lock.class);
            assertEquals("order:#{#orderId}", lock.key());
        }
    }

    // ======================== @Idempotent 注解基础测试 ========================

    @Nested
    @DisplayName("@Idempotent 注解基础测试")
    class IdempotentAnnotationBasicTests {

        @Test
        @DisplayName("应存在于方法级别")
        void shouldBeAppliedToMethod() throws NoSuchMethodException {
            Method method = TestService.class.getMethod("pay", Long.class);
            Idempotent ann = method.getAnnotation(Idempotent.class);
            assertNotNull(ann, "@Idempotent 注解应存在于方法上");
        }

        @Test
        @DisplayName("Target 应为 METHOD")
        void targetShouldBeMethod() {
            java.lang.annotation.Target target = Idempotent.class.getAnnotation(java.lang.annotation.Target.class);
            assertNotNull(target);
            boolean hasMethod = false;
            for (java.lang.annotation.ElementType et : target.value()) {
                if (et == java.lang.annotation.ElementType.METHOD) hasMethod = true;
            }
            assertTrue(hasMethod, "@Idempotent 的 @Target 应包含 METHOD");
        }

        @Test
        @DisplayName("Retention 应为 RUNTIME")
        void retentionShouldBeRuntime() {
            java.lang.annotation.Retention retention = Idempotent.class.getAnnotation(java.lang.annotation.Retention.class);
            assertNotNull(retention);
            assertEquals(java.lang.annotation.RetentionPolicy.RUNTIME, retention.value(),
                    "@Idempotent 的 @Retention 应为 RUNTIME");
        }

        @Test
        @DisplayName("应有 @Documented 元注解")
        void shouldHaveDocumentedMetaAnnotation() {
            Documented doc = Idempotent.class.getAnnotation(Documented.class);
            assertNotNull(doc, "@Idempotent 应有 @Documented 注解");
        }
    }

    // ======================== @Idempotent 属性默认值测试 ========================

    @Nested
    @DisplayName("@Idempotent 属性默认值测试")
    class IdempotentDefaultValuesTests {

        @Test
        @DisplayName("ttl 默认应为 3600")
        void ttlDefaultShouldBe3600() throws NoSuchMethodException {
            Method method = TestService.class.getMethod("defaultIdempotentMethod", Long.class);
            Idempotent ann = method.getAnnotation(Idempotent.class);
            assertEquals(3600L, ann.ttl(), "ttl 默认值应为 3600");
        }

        @Test
        @DisplayName("key 无默认值，必须由用户显式指定")
        void keyHasNoDefaultValue() throws NoSuchMethodException {
            Method method = TestService.class.getMethod("defaultIdempotentMethod", Long.class);
            Idempotent ann = method.getAnnotation(Idempotent.class);
            assertNotNull(ann.key());
            assertFalse(ann.key().isEmpty());
        }
    }

    // ======================== @Idempotent 自定义值测试 ========================

    @Nested
    @DisplayName("@Idempotent 自定义值测试")
    class IdempotentCustomValuesTests {

        @Test
        @DisplayName("自定义 ttl=3600 应被正确读取")
        void customTtlShouldBeReadCorrectly() throws NoSuchMethodException {
            Method method = TestService.class.getMethod("pay", Long.class);
            Idempotent ann = method.getAnnotation(Idempotent.class);
            assertEquals(3600L, ann.ttl());
        }

        @Test
        @DisplayName("SpEL key 表达式应被完整保留")
        void spELKeyShouldBePreserved() throws NoSuchMethodException {
            Method method = TestService.class.getMethod("pay", Long.class);
            Idempotent ann = method.getAnnotation(Idempotent.class);
            assertEquals("pay:#{#orderId}", ann.key());
        }
    }

    // ======================== 两注解不冲突测试 ========================

    @Nested
    @DisplayName("@Lock 与 @Idempotent 独立定义测试")
    class LockAndIdempotentIndependenceTests {

        @Test
        @DisplayName("@Lock 和 @Idempotent 可分别标注在不同方法上")
        void lockAndIdempotentCanBeUsedOnSeparateMethods() throws NoSuchMethodException {
            // @Lock 方法
            Method lockMethod = TestService.class.getMethod("processOrder", Long.class);
            assertNotNull(lockMethod.getAnnotation(Lock.class));

            // @Idempotent 方法
            Method idemMethod = TestService.class.getMethod("pay", Long.class);
            assertNotNull(idemMethod.getAnnotation(Idempotent.class));
        }

        @Test
        @DisplayName("@Lock 注解不要求标注者方法有返回值")
        void lockAnnotationDoesNotRequireReturnValue() throws NoSuchMethodException {
            Method method = TestService.class.getMethod("processOrder", Long.class);
            assertNotNull(method.getAnnotation(Lock.class));
            assertEquals(void.class, method.getReturnType(), "void 方法也可标注 @Lock");
        }

        @Test
        @DisplayName("@Idempotent 注解不要求标注者方法有返回值")
        void idempotentAnnotationDoesNotRequireReturnValue() throws NoSuchMethodException {
            Method method = TestService.class.getMethod("defaultIdempotentMethod", Long.class);
            assertNotNull(method.getAnnotation(Idempotent.class));
            assertEquals(void.class, method.getReturnType(), "void 方法也可标注 @Idempotent");
        }
    }
}

package io.github.leylaragg.letool.tool.util;

import io.github.leylaragg.letool.tool.reflection.ReflectionErrorCode;
import io.github.leylaragg.letool.tool.reflection.ReflectionOperationException;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ReflectUtil} 的字段访问、重载调用、组合注解和泛型解析契约测试。
 */
class ReflectUtilTest {

    /** 可作为组合注解元注解使用的测试标记。 */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @interface Marked {
    }

    /** 带有元注解的组合标记。 */
    @Marked
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface ComposedMarked {
    }

    /** 包含私有字段的父类。 */
    static class Parent {

        private String parentValue;
    }

    /** 提供字段和重载方法的测试目标。 */
    @ComposedMarked
    static final class TestTarget extends Parent {

        private final IllegalStateException failure = new IllegalStateException("target failure");

        /**
         * 返回私有方法结果。
         *
         * @return 私有方法结果
         */
        private String secret() {
            return "secret";
        }

        /**
         * 处理整数重载。
         *
         * @param value 整数参数
         * @return 整数重载结果
         */
        private String overloaded(Integer value) {
            return "integer";
        }

        /**
         * 处理数字重载。
         *
         * @param value 数字参数
         * @return 数字重载结果
         */
        private String overloaded(Number value) {
            return "number";
        }

        /**
         * 处理父类型参数。
         *
         * @param value 数字参数
         * @return 父类型匹配结果
         */
        private String numberOnly(Number value) {
            return "number";
        }

        /**
         * 处理字符串歧义候选。
         *
         * @param value 字符串参数
         * @return 字符串结果
         */
        private String ambiguous(String value) {
            return "string";
        }

        /**
         * 处理整数歧义候选。
         *
         * @param value 整数参数
         * @return 整数结果
         */
        private String ambiguous(Integer value) {
            return "integer";
        }

        /**
         * 抛出固定目标异常。
         *
         * @return 不会正常返回
         */
        private String fail() {
            throw failure;
        }
    }

    /** 泛型仓储测试接口。 */
    interface Repository<T> {
    }

    /** 固定字符串泛型的仓储实现。 */
    static final class StringRepository implements Repository<String> {
    }

    /**
     * 验证父类私有字段可以被查找和读写，缺失字段同时提供可选与严格契约。
     */
    @Test
    void shouldAccessInheritedFieldWithOptionalAndStrictLookup() {
        TestTarget target = new TestTarget();

        ReflectUtil.setFieldValue(target, "parentValue", "parent");
        List<Field> fields = ReflectUtil.getAllFields(TestTarget.class);

        assertEquals("parent", ReflectUtil.getFieldValue(target, "parentValue"));
        assertTrue(ReflectUtil.findField(TestTarget.class, "parentValue").isPresent());
        assertFalse(ReflectUtil.findField(TestTarget.class, "missing").isPresent());
        assertTrue(fields.stream().anyMatch(field -> field.getName().equals("parentValue")));
        assertThrows(UnsupportedOperationException.class, () -> fields.add(fields.get(0)));

        ReflectionOperationException exception = assertThrows(
                ReflectionOperationException.class,
                () -> ReflectUtil.requireField(TestTarget.class, "missing")
        );
        assertEquals(ReflectionErrorCode.MEMBER_NOT_FOUND.getCode(), exception.getCode());
    }

    /**
     * 验证最接近重载和父类型参数能够匹配，空参数造成歧义时明确失败。
     */
    @Test
    void shouldInvokeClosestOverloadAndRejectAmbiguity() {
        TestTarget target = new TestTarget();

        assertEquals("secret", ReflectUtil.invokeMethod(target, "secret"));
        assertEquals("integer", ReflectUtil.invokeMethod(target, "overloaded", 1));
        assertEquals("number", ReflectUtil.invokeMethod(target, "numberOnly", 1));

        ReflectionOperationException exception = assertThrows(
                ReflectionOperationException.class,
                () -> ReflectUtil.invokeMethod(target, "ambiguous", new Object[]{null})
        );
        assertEquals(ReflectionErrorCode.MEMBER_NOT_FOUND.getCode(), exception.getCode());
    }

    /**
     * 验证目标方法抛出的业务异常作为直接原因保留，不被反射包装层覆盖。
     */
    @Test
    void shouldPreserveTargetMethodFailure() {
        TestTarget target = new TestTarget();

        ReflectionOperationException exception = assertThrows(
                ReflectionOperationException.class,
                () -> ReflectUtil.invokeMethod(target, "fail")
        );

        assertEquals(ReflectionErrorCode.METHOD_INVOCATION_FAILED.getCode(), exception.getCode());
        assertSame(target.failure, exception.getCause());
    }

    /**
     * 验证 Spring 合并注解语义和指定泛型基类解析可以直接使用。
     */
    @Test
    void shouldResolveMergedAnnotationAndGenericInterface() {
        Marked annotation = ReflectUtil.getAnnotation(TestTarget.class, Marked.class);

        assertNotNull(annotation);
        assertEquals(
                String.class,
                ReflectUtil.resolveTypeArgument(StringRepository.class, Repository.class, 0)
                        .orElseThrow()
        );
    }

    /**
     * 验证命令型反射操作不会静默忽略空目标。
     */
    @Test
    void shouldRejectNullInvocationTarget() {
        ReflectionOperationException exception = assertThrows(
                ReflectionOperationException.class,
                () -> ReflectUtil.invokeMethod(null, "secret")
        );

        assertEquals(ReflectionErrorCode.INVALID_ARGUMENT.getCode(), exception.getCode());
    }
}

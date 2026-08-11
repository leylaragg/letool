package com.github.leyland.letool.tool.util;

import com.github.leyland.letool.tool.reflection.ReflectionErrorCode;
import com.github.leyland.letool.tool.reflection.ReflectionOperationException;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ClassUtil} 的扫描顺序、静态初始化安全和元数据过滤契约测试。
 */
class ClassUtilTest {

    /** 测试类所在扫描包。 */
    private static final String TEST_PACKAGE = ClassUtilTest.class.getPackageName();

    /** 记录候选类静态初始化状态。 */
    static final class InitializationState {

        private static boolean initialized;

        /** 状态容器不允许实例化。 */
        private InitializationState() {
        }
    }

    /** 带有可观测静态初始化副作用的扫描候选类。 */
    static final class InitializationProbe {

        static {
            InitializationState.initialized = true;
        }

        /** 扫描候选类不允许实例化。 */
        private InitializationProbe() {
        }
    }

    /** 可作为组合注解元注解使用的扫描标记。 */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @interface ScanMarked {
    }

    /** 组合扫描标记。 */
    @ScanMarked
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface ComposedScanMarked {
    }

    /** 接口扫描目标。 */
    interface ScanContract {
    }

    /** 应被注解和接口扫描同时发现的候选类。 */
    @ComposedScanMarked
    static final class AnnotatedCandidate implements ScanContract {
    }

    /** 应被接口扫描发现的普通候选类。 */
    static final class ConcreteCandidate implements ScanContract {
    }

    /** 不应出现在具体实现结果中的抽象候选类。 */
    abstract static class AbstractCandidate implements ScanContract {
    }

    /**
     * 验证类名扫描不加载类型，Class 扫描只加载定义但不执行静态初始化。
     */
    @Test
    void shouldScanWithoutTriggeringStaticInitialization() {
        InitializationState.initialized = false;
        ClassLoader classLoader = ClassUtilTest.class.getClassLoader();

        List<String> names = ClassUtil.scanClassNames(TEST_PACKAGE, classLoader);
        List<Class<?>> classes = ClassUtil.scan(TEST_PACKAGE, classLoader);

        assertTrue(names.contains(InitializationProbe.class.getName()));
        assertTrue(classes.contains(InitializationProbe.class));
        assertFalse(InitializationState.initialized);
        assertEquals(names.stream().sorted().toList(), names);
    }

    /**
     * 验证元注解过滤和接口过滤只加载符合条件的具体类型。
     */
    @Test
    void shouldFilterByMetaAnnotationAndInterface() {
        ClassLoader classLoader = ClassUtilTest.class.getClassLoader();
        List<Class<?>> annotated = ClassUtil.scanByAnnotation(
                TEST_PACKAGE,
                ScanMarked.class,
                classLoader
        );
        List<Class<? extends ScanContract>> implementations = ClassUtil.scanByInterface(
                TEST_PACKAGE,
                ScanContract.class,
                classLoader
        );

        assertTrue(annotated.contains(AnnotatedCandidate.class));
        assertTrue(implementations.contains(AnnotatedCandidate.class));
        assertTrue(implementations.contains(ConcreteCandidate.class));
        assertFalse(implementations.contains(AbstractCandidate.class));
        assertTrue(ClassUtil.isPresent(AnnotatedCandidate.class.getName(), classLoader));
        assertFalse(ClassUtil.isPresent("com.example.DoesNotExist", classLoader));
    }

    /**
     * 验证包名包含通配符等无效内容时返回统一参数错误码。
     */
    @Test
    void shouldRejectInvalidPackageName() {
        ReflectionOperationException exception = assertThrows(
                ReflectionOperationException.class,
                () -> ClassUtil.scan("com.example.*")
        );

        assertEquals(ReflectionErrorCode.INVALID_ARGUMENT.getCode(), exception.getCode());
    }
}

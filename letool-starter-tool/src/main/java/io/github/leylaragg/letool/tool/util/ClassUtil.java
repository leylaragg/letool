package io.github.leylaragg.letool.tool.util;

import io.github.leylaragg.letool.tool.reflection.ReflectionOperationException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.ClassUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * 基于 Spring 类路径元数据读取器的安全类扫描便捷工具。
 *
 * <p>{@code scanClassNames} 只读取字节码元数据，不加载类型；返回 {@code Class<?>} 的扫描方法会
 * 将候选类型定义加载进 JVM，但使用禁止初始化的加载方式，不执行静态初始化块。扫描结果按类名稳定
 * 排序并去重，任一资源读取或候选类加载失败时快速失败，不返回不完整结果。</p>
 */
public final class ClassUtil {

    /** 合法 Java 包名模式，禁止通配符、路径跳转和资源协议注入。 */
    private static final Pattern PACKAGE_NAME_PATTERN = Pattern.compile(
            "[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*"
                    + "(?:\\.[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*)*"
    );

    /** 工具类不允许实例化。 */
    private ClassUtil() {
    }

    /**
     * 扫描指定包下的独立类名称，不加载候选类型。
     *
     * @param basePackage 基础包名
     * @return 按类名排序的不可修改列表
     * @throws ReflectionOperationException 包名无效或资源读取失败时抛出
     */
    public static List<String> scanClassNames(String basePackage) {
        return scanClassNames(basePackage, defaultClassLoader());
    }

    /**
     * 使用指定类加载器扫描独立类名称，不加载候选类型。
     *
     * @param basePackage 基础包名
     * @param classLoader 资源解析和后续类加载使用的类加载器
     * @return 按类名排序的不可修改列表
     * @throws ReflectionOperationException 参数无效或资源读取失败时抛出
     */
    public static List<String> scanClassNames(
            String basePackage,
            ClassLoader classLoader) {
        return scanClassNames(basePackage, classLoader, null);
    }

    /**
     * 扫描指定包下的独立类型，加载类定义但不执行静态初始化。
     *
     * @param basePackage 基础包名
     * @return 按类名排序的不可修改类型列表
     * @throws ReflectionOperationException 参数无效、资源读取或类型加载失败时抛出
     */
    public static List<Class<?>> scan(String basePackage) {
        return scan(basePackage, defaultClassLoader());
    }

    /**
     * 使用指定类加载器扫描独立类型，加载类定义但不执行静态初始化。
     *
     * @param basePackage 基础包名
     * @param classLoader 资源解析和类型加载使用的类加载器
     * @return 按类名排序的不可修改类型列表
     * @throws ReflectionOperationException 参数无效、资源读取或类型加载失败时抛出
     */
    public static List<Class<?>> scan(
            String basePackage,
            ClassLoader classLoader) {
        return loadClasses(scanClassNames(basePackage, classLoader), classLoader, basePackage);
    }

    /**
     * 扫描带有指定直接注解或元注解的类型。
     *
     * @param basePackage 基础包名
     * @param annotationClass 注解类型
     * @return 按类名排序的不可修改类型列表
     * @throws ReflectionOperationException 参数无效、资源读取或类型加载失败时抛出
     */
    public static List<Class<?>> scanByAnnotation(
            String basePackage,
            Class<? extends Annotation> annotationClass) {
        return scanByAnnotation(basePackage, annotationClass, defaultClassLoader());
    }

    /**
     * 使用指定类加载器扫描带有直接注解或元注解的类型。
     *
     * @param basePackage 基础包名
     * @param annotationClass 注解类型
     * @param classLoader 资源解析和类型加载使用的类加载器
     * @return 按类名排序的不可修改类型列表
     * @throws ReflectionOperationException 参数无效、资源读取或类型加载失败时抛出
     */
    public static List<Class<?>> scanByAnnotation(
            String basePackage,
            Class<? extends Annotation> annotationClass,
            ClassLoader classLoader) {
        if (annotationClass == null) {
            throw ReflectionOperationException.invalidArgument("annotationClass");
        }
        TypeFilter filter = new AnnotationTypeFilter(annotationClass, true, true);
        List<String> classNames = scanClassNames(basePackage, classLoader, filter);
        return loadClasses(classNames, classLoader, basePackage);
    }

    /**
     * 扫描实现指定接口的具体类。
     *
     * @param basePackage 基础包名
     * @param interfaceClass 目标接口
     * @param <T> 接口类型
     * @return 排除接口、抽象类、注解和枚举后的不可修改类型列表
     * @throws ReflectionOperationException 参数无效、资源读取或类型加载失败时抛出
     */
    public static <T> List<Class<? extends T>> scanByInterface(
            String basePackage,
            Class<T> interfaceClass) {
        return scanByInterface(basePackage, interfaceClass, defaultClassLoader());
    }

    /**
     * 使用指定类加载器扫描实现指定接口的具体类。
     *
     * @param basePackage 基础包名
     * @param interfaceClass 目标接口
     * @param classLoader 资源解析和类型加载使用的类加载器
     * @param <T> 接口类型
     * @return 排除接口、抽象类、注解和枚举后的不可修改类型列表
     * @throws ReflectionOperationException 参数无效、资源读取或类型加载失败时抛出
     */
    public static <T> List<Class<? extends T>> scanByInterface(
            String basePackage,
            Class<T> interfaceClass,
            ClassLoader classLoader) {
        if (interfaceClass == null || !interfaceClass.isInterface()) {
            throw ReflectionOperationException.invalidArgument("interfaceClass");
        }
        TypeFilter filter = new AssignableTypeFilter(interfaceClass);
        List<Class<?>> candidates = loadClasses(
                scanClassNames(basePackage, classLoader, filter),
                classLoader,
                basePackage
        );
        List<Class<? extends T>> implementations = new ArrayList<>();
        for (Class<?> candidate : candidates) {
            int modifiers = candidate.getModifiers();
            if (interfaceClass.isAssignableFrom(candidate)
                    && !candidate.isInterface()
                    && !candidate.isAnnotation()
                    && !candidate.isEnum()
                    && !Modifier.isAbstract(modifiers)) {
                implementations.add(candidate.asSubclass(interfaceClass));
            }
        }
        return List.copyOf(implementations);
    }

    /**
     * 获取目标类直接声明实现的接口。
     *
     * @param clazz 目标类型，允许为空
     * @return 接口数组；类型为空时返回空数组
     */
    public static Class<?>[] getInterfaces(Class<?> clazz) {
        return clazz == null ? new Class<?>[0] : clazz.getInterfaces();
    }

    /**
     * 判断默认类加载器中是否存在指定类。
     *
     * @param className 全限定类名
     * @return 类型及其链接依赖可用时返回 {@code true}
     * @throws ReflectionOperationException 类名无效时抛出
     */
    public static boolean isPresent(String className) {
        return isPresent(className, defaultClassLoader());
    }

    /**
     * 判断指定类加载器中是否存在指定类。
     *
     * @param className 全限定类名
     * @param classLoader 待使用类加载器
     * @return 类型及其链接依赖可用时返回 {@code true}
     * @throws ReflectionOperationException 参数无效时抛出
     */
    public static boolean isPresent(String className, ClassLoader classLoader) {
        requireText(className, "className");
        requireClassLoader(classLoader);
        return ClassUtils.isPresent(className, classLoader);
    }

    /**
     * 读取并过滤类路径元数据。
     *
     * @param basePackage 基础包名
     * @param classLoader 资源解析类加载器
     * @param filter 可选元数据过滤器，为空时接受全部独立类型
     * @return 稳定排序且去重的不可修改类名列表
     */
    private static List<String> scanClassNames(
            String basePackage,
            ClassLoader classLoader,
            TypeFilter filter) {
        requirePackageName(basePackage);
        requireClassLoader(classLoader);
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(classLoader);
        MetadataReaderFactory readerFactory = new CachingMetadataReaderFactory(resolver);
        Set<String> classNames = new TreeSet<>();
        String pattern = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX
                + ClassUtils.convertClassNameToResourcePath(basePackage)
                + "/**/*.class";

        try {
            for (Resource resource : resolver.getResources(pattern)) {
                if (!resource.isReadable()) {
                    continue;
                }
                MetadataReader reader = readerFactory.getMetadataReader(resource);
                ClassMetadata metadata = reader.getClassMetadata();
                String className = metadata.getClassName();
                if (!metadata.isIndependent()
                        || className.endsWith("package-info")
                        || className.endsWith("module-info")) {
                    continue;
                }
                if (filter == null || filter.match(reader, readerFactory)) {
                    classNames.add(className);
                }
            }
        } catch (Exception exception) {
            throw ReflectionOperationException.classScanFailed(basePackage, exception);
        }
        return List.copyOf(classNames);
    }

    /**
     * 加载类定义但禁止执行静态初始化。
     *
     * @param classNames 已排序候选类名
     * @param classLoader 类型加载器
     * @param scanScope 安全扫描范围，用于统一异常
     * @return 保持输入顺序的不可修改类型列表
     */
    private static List<Class<?>> loadClasses(
            List<String> classNames,
            ClassLoader classLoader,
            String scanScope) {
        List<Class<?>> classes = new ArrayList<>(classNames.size());
        for (String className : classNames) {
            try {
                classes.add(Class.forName(className, false, classLoader));
            } catch (ClassNotFoundException | LinkageError | RuntimeException exception) {
                throw ReflectionOperationException.classScanFailed(scanScope, exception);
            }
        }
        return List.copyOf(classes);
    }

    /**
     * 获取 Spring 约定的默认类加载器并提供可靠回退。
     *
     * @return 非空默认类加载器
     */
    private static ClassLoader defaultClassLoader() {
        ClassLoader classLoader = ClassUtils.getDefaultClassLoader();
        return classLoader == null ? ClassUtil.class.getClassLoader() : classLoader;
    }

    /**
     * 校验基础包名不包含通配符或路径片段。
     *
     * @param basePackage 待校验包名
     */
    private static void requirePackageName(String basePackage) {
        if (basePackage == null
                || basePackage.isBlank()
                || !PACKAGE_NAME_PATTERN.matcher(basePackage).matches()) {
            throw ReflectionOperationException.invalidArgument("basePackage");
        }
    }

    /**
     * 校验类加载器非空。
     *
     * @param classLoader 待校验类加载器
     */
    private static void requireClassLoader(ClassLoader classLoader) {
        if (classLoader == null) {
            throw ReflectionOperationException.invalidArgument("classLoader");
        }
    }

    /**
     * 校验文本参数非空白。
     *
     * @param value 待校验文本
     * @param parameterName 公开参数名称
     */
    private static void requireText(String value, String parameterName) {
        if (value == null || value.isBlank()) {
            throw ReflectionOperationException.invalidArgument(parameterName);
        }
    }
}

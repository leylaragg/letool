package com.github.leyland.letool.tool.util;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * 类扫描工具——基于 Spring 的 ClassPath 元数据扫描.
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>扫描指定包下的所有类</li>
 *   <li>按接口或注解过滤（如找出所有实现某接口的类）</li>
 *   <li>判断类是否存在（classpath 检测）</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 扫描包下所有类
 * List<Class<?>> classes = ClassUtil.scan("com.example.service");
 *
 * // 找出所有实现了 DataHandler 接口的类（排除接口和抽象类）
 * List<Class<? extends DataHandler>> handlers =
 *     ClassUtil.scanByInterface("com.example.handler", DataHandler.class);
 *
 * // 找出所有带 @Component 注解的类
 * List<Class<?>> components =
 *     ClassUtil.scanByAnnotation("com.example", Component.class);
 *
 * // 检测类是否在 classpath 中
 * if (ClassUtil.isPresent("org.apache.hc.client5.http.classic.HttpClient")) { ... }
 * }</pre>
 *
 * <p>注意：类扫描是 IO 操作，不适合在高频路径调用，建议在启动时执行并缓存结果.</p>
 */
public final class ClassUtil {

    private ClassUtil() {}

    private static final ResourcePatternResolver RESOLVER = new PathMatchingResourcePatternResolver();
    private static final MetadataReaderFactory READER_FACTORY = new CachingMetadataReaderFactory();

    /**
     * 扫描指定包下的所有类（递归扫描子包）.
     *
     * <p>使用 Spring 的 {@link PathMatchingResourcePatternResolver} 扫描 classpath，
     * 通过 ASM 读取字节码元数据（不加载类到 JVM），避免触发静态初始化.</p>
     *
     * @param basePackage 基础包名（如 "com.example.service"）
     * @return 包下所有类的列表
     * @throws RuntimeException 如果包路径不存在或无读取权限
     */
    public static List<Class<?>> scan(String basePackage) {
        List<Class<?>> classes = new ArrayList<>();
        try {
            String pattern = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX
                    + ClassUtils.convertClassNameToResourcePath(basePackage) + "/**/*.class";
            Resource[] resources = RESOLVER.getResources(pattern);
            for (Resource resource : resources) {
                MetadataReader reader = READER_FACTORY.getMetadataReader(resource);
                classes.add(Class.forName(reader.getClassMetadata().getClassName()));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to scan package: " + basePackage, e);
        }
        return classes;
    }

    /**
     * 扫描指定包下实现了指定接口的具体类（排除接口和抽象类）.
     *
     * @param basePackage    基础包名
     * @param interfaceClass 目标接口类型
     * @param <T>            接口泛型
     * @return 实现了该接口的具体类的列表
     */
    @SuppressWarnings("unchecked")
    public static <T> List<Class<? extends T>> scanByInterface(String basePackage, Class<T> interfaceClass) {
        List<Class<? extends T>> result = new ArrayList<>();
        for (Class<?> clazz : scan(basePackage)) {
            if (interfaceClass.isAssignableFrom(clazz) && !clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers())) {
                result.add((Class<? extends T>) clazz);
            }
        }
        return result;
    }

    /**
     * 扫描指定包下带有指定注解的类.
     *
     * @param basePackage     基础包名
     * @param annotationClass 注解类型
     * @return 带有该注解的类的列表
     */
    public static List<Class<?>> scanByAnnotation(String basePackage, Class<? extends java.lang.annotation.Annotation> annotationClass) {
        List<Class<?>> result = new ArrayList<>();
        for (Class<?> clazz : scan(basePackage)) {
            if (clazz.isAnnotationPresent(annotationClass)) {
                result.add(clazz);
            }
        }
        return result;
    }

    /**
     * 获取指定类直接实现的所有接口.
     *
     * @param clazz 目标类
     * @return 接口数组，{@code clazz} 为 {@code null} 返回空数组
     */
    public static Class<?>[] getInterfaces(Class<?> clazz) {
        return clazz == null ? new Class<?>[0] : clazz.getInterfaces();
    }

    /**
     * 判断 classpath 中是否存在指定类（常用于引擎检测、条件激活）.
     *
     * <p>内部使用 {@link Class#forName(String)}，不会触发类初始化（不执行 static 块）.</p>
     *
     * @param className 全限定类名
     * @return {@code true} 如果类存在
     */
    public static boolean isPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}

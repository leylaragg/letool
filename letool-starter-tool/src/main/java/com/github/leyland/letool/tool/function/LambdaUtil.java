package com.github.leyland.letool.tool.function;

import java.beans.Introspector;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lambda 工具——从方法引用（{@code User::getName}）中解析属性名.
 *
 * <h3>原理</h3>
 * <p>通过 JDK 的 {@link SerializedLambda} 机制，在运行时解析 Lambda 表达式的
 * {@code writeReplace} 方法，获取底层实现方法名（如 {@code getName}），
 * 再通过 JavaBeans 规范去掉 {@code get/is} 前缀得到属性名（如 {@code name}）.</p>
 *
 * <h3>使用前提</h3>
 * <p>Lambda 必须实现 {@link SFunction} 接口（继承了 {@link java.io.Serializable}），
 * 因为 {@link SerializedLambda} 仅支持可序列化的 Lambda.</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 获取属性名（类型安全，无需字符串）
 * String name = LambdaUtil.getPropertyName(User::getName);    // → "name"
 * String status = LambdaUtil.getPropertyName(User::getStatus); // → "status"
 *
 * // 配合 Lambda 查询构造器
 * query.eq(User::getName, "张三");     // 等价于 WHERE name = '张三'
 * query.orderByDesc(User::getAge);     // 等价于 ORDER BY age DESC
 * }</pre>
 *
 * <p>注意：此方法有反射开销，结果会被缓存，建议在类型安全的查询构建器中使用.</p>
 */
public final class LambdaUtil {

    /** SerializedLambda 缓存——key 为 Lambda 类名 */
    private static final Map<String, SerializedLambda> CACHE = new ConcurrentHashMap<>();

    private LambdaUtil() {}

    /**
     * 从 SFunction（方法引用 Lambda）中提取属性名.
     *
     * <p>例如传入 {@code User::getName}，提取字符串 {@code "name"}.</p>
     *
     * @param func 可序列化的方法引用（如 {@code User::getName}）
     * @param <T>  源对象类型
     * @param <R>  属性类型
     * @return 属性名（JavaBeans 规范，首字母小写）
     * @throws RuntimeException 如果 Lambda 解析失败（如传入非序列化 Lambda）
     */
    public static <T, R> String getPropertyName(SFunction<T, R> func) {
        SerializedLambda lambda = resolve(func);
        String methodName = lambda.getImplMethodName();
        String prefix = methodName.startsWith("is") ? "is" : "get";
        return Introspector.decapitalize(methodName.substring(prefix.length()));
    }

    /**
     * 解析 Lambda 表达式为 SerializedLambda 实例.
     *
     * <p>通过反射调用 Lambda 对象的 {@code writeReplace} 方法获取序列化信息.
     * 结果缓存到 ConcurrentHashMap 中，同一类只需解析一次.</p>
     */
    @SuppressWarnings("unchecked")
    private static <T> SerializedLambda resolve(SFunction<T, ?> func) {
        Class<?> clazz = func.getClass();
        String name = clazz.getName();
        return CACHE.computeIfAbsent(name, k -> {
            try {
                Method writeReplace = clazz.getDeclaredMethod("writeReplace");
                writeReplace.setAccessible(true);
                return (SerializedLambda) writeReplace.invoke(func);
            } catch (Exception e) {
                throw new RuntimeException("Failed to resolve lambda", e);
            }
        });
    }

    /**
     * 可序列化的 Function 接口——用于 Lambda 属性名解析.
     *
     * <p>继承 {@link java.util.function.Function} 和 {@link java.io.Serializable}，
     * 使方法引用（如 {@code User::getName}）生成的 Lambda 对象可序列化，
     * 从而能被 {@link LambdaUtil#getPropertyName(SFunction)} 解析.</p>
     *
     * @param <T> 输入类型
     * @param <R> 返回类型
     */
    @FunctionalInterface
    public interface SFunction<T, R> extends java.util.function.Function<T, R>, java.io.Serializable {}
}

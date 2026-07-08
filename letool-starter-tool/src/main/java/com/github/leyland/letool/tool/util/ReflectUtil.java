package com.github.leyland.letool.tool.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 反射工具类——字段、方法、注解、泛型的便捷操作.
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li><b>字段操作</b>：获取类及父类的字段、读写字段值（自动 setAccessible）</li>
 *   <li><b>方法操作</b>：查找方法（含父类）、反射调用</li>
 *   <li><b>注解操作</b>：读取类或字段上的注解（含继承查找）</li>
 *   <li><b>泛型解析</b>：获取父类泛型的实际类型参数</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 写入私有字段
 * ReflectUtil.setFieldValue(obj, "name", "张三");
 *
 * // 读取私有字段
 * String name = ReflectUtil.getFieldValue(obj, "name");
 *
 * // 获取泛型类型
 * Class<?> entityClass = ReflectUtil.getSuperClassGenericType(getClass(), 0);
 * }</pre>
 *
 * <p>注意：所有方法已处理访问权限（setAccessible），但反射操作性能远低于直接调用，
 * 高频场景请使用 {@link BeanUtil#copyFast(Object, Class)} 或 Lambda 表达式.</p>
 */
public final class ReflectUtil {

    private ReflectUtil() {}

    // ======================== 字段操作 ========================

    /**
     * 获取指定名称的字段（递归查找父类）.
     *
     * @param clazz     目标类
     * @param fieldName 字段名
     * @return Field 对象，找不到返回 {@code null}
     */
    public static Field getField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /**
     * 获取类及其所有父类的所有字段（包括私有字段）.
     *
     * @param clazz 目标类
     * @return 字段列表（含继承的字段），不包含 Object.class 的字段
     */
    public static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
        }
        return fields;
    }

    /**
     * 读取对象指定字段的值（自动处理私有字段）.
     *
     * @param obj       目标对象
     * @param fieldName 字段名
     * @param <T>       字段类型
     * @return 字段值，对象为 {@code null} 或字段不存在返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    public static <T> T getFieldValue(Object obj, String fieldName) {
        if (obj == null) return null;
        Field field = getField(obj.getClass(), fieldName);
        if (field == null) return null;
        field.setAccessible(true);
        try {
            return (T) field.get(obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Cannot access field " + fieldName, e);
        }
    }

    /**
     * 设置对象指定字段的值（自动处理私有字段）.
     *
     * @param obj       目标对象
     * @param fieldName 字段名
     * @param value     新值
     * @throws IllegalArgumentException 如果字段不存在
     */
    public static void setFieldValue(Object obj, String fieldName, Object value) {
        if (obj == null) return;
        Field field = getField(obj.getClass(), fieldName);
        if (field == null) throw new IllegalArgumentException("Field not found: " + fieldName);
        field.setAccessible(true);
        try {
            field.set(obj, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Cannot set field " + fieldName, e);
        }
    }

    // ======================== 方法操作 ========================

    /**
     * 获取指定签名的方法（递归查找父类）.
     *
     * @param clazz      目标类
     * @param methodName 方法名
     * @param paramTypes 参数类型列表
     * @return Method 对象，找不到返回 {@code null}
     */
    public static Method getMethod(Class<?> clazz, String methodName, Class<?>... paramTypes) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredMethod(methodName, paramTypes);
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /**
     * 反射调用方法.
     *
     * <p>注意：通过参数实例的实际类型推断参数类型，无法区分 {@code int} 和 {@code long} 等重载方法.</p>
     *
     * @param obj        目标对象
     * @param methodName 方法名
     * @param args       方法参数
     * @param <T>        返回类型
     * @return 方法返回值，对象为 {@code null} 返回 {@code null}
     * @throws IllegalArgumentException 如果找不到匹配的方法
     */
    @SuppressWarnings("unchecked")
    public static <T> T invokeMethod(Object obj, String methodName, Object... args) {
        if (obj == null) return null;
        Class<?>[] paramTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            // null 参数默认使用 Object.class，避免 NPE
            paramTypes[i] = args[i] != null ? args[i].getClass() : Object.class;
        }
        Method method = getMethod(obj.getClass(), methodName, paramTypes);
        if (method == null) throw new IllegalArgumentException("Method not found: " + methodName);
        method.setAccessible(true);
        try {
            return (T) method.invoke(obj, args);
        } catch (Exception e) {
            throw new RuntimeException("Cannot invoke method " + methodName, e);
        }
    }

    // ======================== 注解 ========================

    /**
     * 获取类上的指定注解（递归查找父类）.
     *
     * @param clazz           目标类
     * @param annotationClass 注解类型
     * @param <A>             注解泛型
     * @return 注解实例，找不到返回 {@code null}
     */
    public static <A extends Annotation> A getAnnotation(Class<?> clazz, Class<A> annotationClass) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            A annotation = current.getDeclaredAnnotation(annotationClass);
            if (annotation != null) return annotation;
            current = current.getSuperclass();
        }
        return null;
    }

    /**
     * 获取字段上的指定注解.
     *
     * @param field           字段
     * @param annotationClass 注解类型
     * @param <A>             注解泛型
     * @return 注解实例，找不到返回 {@code null}
     */
    public static <A extends Annotation> A getAnnotation(Field field, Class<A> annotationClass) {
        return field.getAnnotation(annotationClass);
    }

    // ======================== 泛型 ========================

    /**
     * 获取父类的泛型实际类型参数.
     *
     * <p>适用于继承泛型父类的场景，如 {@code class UserMapper extends BaseMapper<User>}.</p>
     *
     * <pre>{@code
     * // 获取 BaseMapper<Order> 中的 Order.class
     * Class<?> entityClass = ReflectUtil.getSuperClassGenericType(getClass(), 0);
     * }</pre>
     *
     * @param clazz 当前类
     * @param index 泛型参数索引（从 0 开始）
     * @return 泛型实际类型，无法解析时返回 {@code Object.class}
     */
    public static Class<?> getSuperClassGenericType(Class<?> clazz, int index) {
        Type genType = clazz.getGenericSuperclass();
        if (!(genType instanceof ParameterizedType)) return Object.class;
        Type[] params = ((ParameterizedType) genType).getActualTypeArguments();
        if (index >= params.length || index < 0) return Object.class;
        if (params[index] instanceof Class) return (Class<?>) params[index];
        return Object.class;
    }
}

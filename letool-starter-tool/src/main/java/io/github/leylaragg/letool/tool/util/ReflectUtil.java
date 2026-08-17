package io.github.leylaragg.letool.tool.util;

import io.github.leylaragg.letool.tool.reflection.ReflectionOperationException;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.MethodInvoker;
import org.springframework.util.ReflectionUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 基于 Spring 反射基础设施的字段、方法、注解和泛型便捷工具。
 *
 * <p>查询型方法同时提供 Optional 和可空兼容入口；读取、写入和调用等命令型方法对无效目标与
 * 缺失成员快速失败。重载匹配委托 Spring 的类型差异算法，并在多个候选权重相同时拒绝猜测。</p>
 */
public final class ReflectUtil {

    /** 工具类不允许实例化。 */
    private ReflectUtil() {
    }

    /**
     * 查询类层次中指定名称的字段。
     *
     * @param clazz 目标类型
     * @param fieldName 字段名称
     * @return 包含匹配字段的 Optional；字段不存在时返回空
     * @throws ReflectionOperationException 参数无效时抛出
     */
    public static Optional<Field> findField(Class<?> clazz, String fieldName) {
        requireType(clazz, "clazz");
        requireText(fieldName, "fieldName");
        return Optional.ofNullable(ReflectionUtils.findField(clazz, fieldName));
    }

    /**
     * 获取类层次中指定名称的字段。
     *
     * <p>该方法保留旧版可空语义；新代码优先使用 {@link #findField(Class, String)} 或
     * {@link #requireField(Class, String)}。</p>
     *
     * @param clazz 目标类型
     * @param fieldName 字段名称
     * @return 匹配字段；字段不存在时返回 {@code null}
     * @throws ReflectionOperationException 参数无效时抛出
     */
    public static Field getField(Class<?> clazz, String fieldName) {
        return findField(clazz, fieldName).orElse(null);
    }

    /**
     * 获取类层次中必须存在的字段。
     *
     * @param clazz 目标类型
     * @param fieldName 字段名称
     * @return 匹配字段
     * @throws ReflectionOperationException 参数无效或字段不存在时抛出
     */
    public static Field requireField(Class<?> clazz, String fieldName) {
        return findField(clazz, fieldName)
                .orElseThrow(() -> ReflectionOperationException.memberNotFound(
                        clazz.getName() + "#" + fieldName
                ));
    }

    /**
     * 获取类层次中的非 synthetic 字段快照。
     *
     * @param clazz 目标类型
     * @return 按子类到父类顺序排列的不可修改字段列表
     * @throws ReflectionOperationException 参数无效时抛出
     */
    public static List<Field> getAllFields(Class<?> clazz) {
        requireType(clazz, "clazz");
        List<Field> fields = new ArrayList<>();
        ReflectionUtils.doWithFields(clazz, fields::add, field -> !field.isSynthetic());
        return List.copyOf(fields);
    }

    /**
     * 读取目标对象的指定字段值。
     *
     * @param object 目标对象
     * @param fieldName 字段名称
     * @param <T> 字段值类型
     * @return 字段值
     * @throws ReflectionOperationException 参数无效、字段不存在或访问失败时抛出
     */
    @SuppressWarnings("unchecked")
    public static <T> T getFieldValue(Object object, String fieldName) {
        requireObject(object, "object");
        Field field = requireField(object.getClass(), fieldName);
        try {
            ReflectionUtils.makeAccessible(field);
            return (T) field.get(object);
        } catch (IllegalAccessException | RuntimeException exception) {
            throw ReflectionOperationException.fieldAccessFailed(
                    object.getClass().getName() + "#" + fieldName,
                    exception
            );
        }
    }

    /**
     * 写入目标对象的指定字段值。
     *
     * @param object 目标对象
     * @param fieldName 字段名称
     * @param value 新字段值
     * @throws ReflectionOperationException 参数无效、字段不存在或写入失败时抛出
     */
    public static void setFieldValue(Object object, String fieldName, Object value) {
        requireObject(object, "object");
        Field field = requireField(object.getClass(), fieldName);
        try {
            ReflectionUtils.makeAccessible(field);
            field.set(object, value);
        } catch (IllegalAccessException | RuntimeException exception) {
            throw ReflectionOperationException.fieldAccessFailed(
                    object.getClass().getName() + "#" + fieldName,
                    exception
            );
        }
    }

    /**
     * 按精确参数类型查询类层次中的方法。
     *
     * @param clazz 目标类型
     * @param methodName 方法名称
     * @param parameterTypes 精确参数类型，不允许包含空元素
     * @return 包含匹配方法的 Optional；方法不存在时返回空
     * @throws ReflectionOperationException 参数无效时抛出
     */
    public static Optional<Method> findMethod(
            Class<?> clazz,
            String methodName,
            Class<?>... parameterTypes) {
        requireType(clazz, "clazz");
        requireText(methodName, "methodName");
        Class<?>[] safeTypes = requireParameterTypes(parameterTypes);
        return Optional.ofNullable(ReflectionUtils.findMethod(clazz, methodName, safeTypes));
    }

    /**
     * 按精确参数类型获取类层次中的方法。
     *
     * <p>该方法保留旧版可空语义；新代码优先使用 {@link #findMethod(Class, String, Class[])} 或
     * {@link #requireMethod(Class, String, Class[])}。</p>
     *
     * @param clazz 目标类型
     * @param methodName 方法名称
     * @param parameterTypes 精确参数类型
     * @return 匹配方法；方法不存在时返回 {@code null}
     * @throws ReflectionOperationException 参数无效时抛出
     */
    public static Method getMethod(
            Class<?> clazz,
            String methodName,
            Class<?>... parameterTypes) {
        return findMethod(clazz, methodName, parameterTypes).orElse(null);
    }

    /**
     * 按精确参数类型获取必须存在的方法。
     *
     * @param clazz 目标类型
     * @param methodName 方法名称
     * @param parameterTypes 精确参数类型
     * @return 匹配方法
     * @throws ReflectionOperationException 参数无效或方法不存在时抛出
     */
    public static Method requireMethod(
            Class<?> clazz,
            String methodName,
            Class<?>... parameterTypes) {
        return findMethod(clazz, methodName, parameterTypes)
                .orElseThrow(() -> ReflectionOperationException.memberNotFound(
                        clazz.getName() + "#" + methodName
                ));
    }

    /**
     * 按运行时参数调用最接近且唯一的方法重载。
     *
     * @param object 目标对象
     * @param methodName 方法名称
     * @param arguments 方法参数；数组本身不得为空
     * @param <T> 方法返回值类型
     * @return 方法返回值
     * @throws ReflectionOperationException 参数无效、方法不存在、重载歧义或目标方法失败时抛出
     */
    @SuppressWarnings("unchecked")
    public static <T> T invokeMethod(
            Object object,
            String methodName,
            Object... arguments) {
        requireObject(object, "object");
        requireText(methodName, "methodName");
        if (arguments == null) {
            throw ReflectionOperationException.invalidArgument("arguments");
        }

        StrictMethodInvoker invoker = new StrictMethodInvoker();
        invoker.setTargetObject(object);
        invoker.setTargetMethod(methodName);
        invoker.setArguments(arguments);
        try {
            invoker.prepare();
            return (T) invoker.invoke();
        } catch (NoSuchMethodException | ClassNotFoundException exception) {
            throw ReflectionOperationException.memberNotFound(
                    object.getClass().getName() + "#" + methodName
            );
        } catch (InvocationTargetException exception) {
            throw ReflectionOperationException.methodInvocationFailed(
                    object.getClass().getName() + "#" + methodName,
                    exception.getTargetException()
            );
        } catch (IllegalAccessException | RuntimeException exception) {
            throw ReflectionOperationException.methodInvocationFailed(
                    object.getClass().getName() + "#" + methodName,
                    exception
            );
        }
    }

    /**
     * 调用已经由业务明确选择的方法。
     *
     * <p>该入口适用于存在重载和空参数、无法仅根据运行时值唯一确定签名的场景。</p>
     *
     * @param object 目标对象
     * @param method 待调用方法
     * @param arguments 方法参数；数组本身不得为空
     * @param <T> 方法返回值类型
     * @return 方法返回值
     * @throws ReflectionOperationException 参数无效或目标方法失败时抛出
     */
    @SuppressWarnings("unchecked")
    public static <T> T invokeMethod(
            Object object,
            Method method,
            Object... arguments) {
        requireObject(object, "object");
        if (method == null) {
            throw ReflectionOperationException.invalidArgument("method");
        }
        if (arguments == null) {
            throw ReflectionOperationException.invalidArgument("arguments");
        }
        if (!method.getDeclaringClass().isAssignableFrom(object.getClass())) {
            throw ReflectionOperationException.invalidArgument("object");
        }

        try {
            ReflectionUtils.makeAccessible(method);
            return (T) method.invoke(object, arguments);
        } catch (InvocationTargetException exception) {
            throw ReflectionOperationException.methodInvocationFailed(
                    method.getDeclaringClass().getName() + "#" + method.getName(),
                    exception.getTargetException()
            );
        } catch (IllegalAccessException | RuntimeException exception) {
            throw ReflectionOperationException.methodInvocationFailed(
                    method.getDeclaringClass().getName() + "#" + method.getName(),
                    exception
            );
        }
    }

    /**
     * 使用 Spring 合并注解语义查询注解。
     *
     * @param element 待查询类、字段或方法
     * @param annotationClass 注解类型
     * @param <A> 注解类型
     * @return 包含直接注解、组合注解或元注解的 Optional
     * @throws ReflectionOperationException 参数无效时抛出
     */
    public static <A extends Annotation> Optional<A> findAnnotation(
            AnnotatedElement element,
            Class<A> annotationClass) {
        if (element == null) {
            throw ReflectionOperationException.invalidArgument("element");
        }
        requireType(annotationClass, "annotationClass");
        return Optional.ofNullable(
                AnnotatedElementUtils.findMergedAnnotation(element, annotationClass)
        );
    }

    /**
     * 获取类上的合并注解并保留旧版可空语义。
     *
     * @param clazz 目标类型
     * @param annotationClass 注解类型
     * @param <A> 注解类型
     * @return 匹配注解；不存在时返回 {@code null}
     * @throws ReflectionOperationException 参数无效时抛出
     */
    public static <A extends Annotation> A getAnnotation(
            Class<?> clazz,
            Class<A> annotationClass) {
        requireType(clazz, "clazz");
        return findAnnotation(clazz, annotationClass).orElse(null);
    }

    /**
     * 获取字段上的合并注解并保留旧版可空语义。
     *
     * @param field 目标字段
     * @param annotationClass 注解类型
     * @param <A> 注解类型
     * @return 匹配注解；不存在时返回 {@code null}
     * @throws ReflectionOperationException 参数无效时抛出
     */
    public static <A extends Annotation> A getAnnotation(
            Field field,
            Class<A> annotationClass) {
        return findAnnotation(field, annotationClass).orElse(null);
    }

    /**
     * 获取直接泛型父类的指定实际类型参数。
     *
     * @param clazz 当前类型
     * @param index 泛型参数索引
     * @return 已解析类型；无法解析或索引无效时返回 {@code Object.class}
     * @throws ReflectionOperationException 类型参数为空时抛出
     */
    public static Class<?> getSuperClassGenericType(Class<?> clazz, int index) {
        requireType(clazz, "clazz");
        if (index < 0) {
            return Object.class;
        }
        return ResolvableType.forClass(clazz)
                .getSuperType()
                .getGeneric(index)
                .resolve(Object.class);
    }

    /**
     * 从指定泛型基类或接口解析实际类型参数。
     *
     * @param sourceClass 具体来源类型
     * @param genericBaseClass 泛型基类或接口
     * @param index 泛型参数索引，必须大于等于零
     * @return 包含已解析类型的 Optional；不属于该层次或无法解析时返回空
     * @throws ReflectionOperationException 参数无效时抛出
     */
    public static Optional<Class<?>> resolveTypeArgument(
            Class<?> sourceClass,
            Class<?> genericBaseClass,
            int index) {
        requireType(sourceClass, "sourceClass");
        requireType(genericBaseClass, "genericBaseClass");
        if (index < 0) {
            throw ReflectionOperationException.invalidArgument("index");
        }
        if (!genericBaseClass.isAssignableFrom(sourceClass)) {
            return Optional.empty();
        }

        Class<?> resolved = ResolvableType.forClass(sourceClass)
                .as(genericBaseClass)
                .getGeneric(index)
                .resolve();
        return Optional.ofNullable(resolved);
    }

    /**
     * 校验类型参数非空。
     *
     * @param type 待校验类型
     * @param parameterName 公开参数名称
     */
    private static void requireType(Class<?> type, String parameterName) {
        if (type == null) {
            throw ReflectionOperationException.invalidArgument(parameterName);
        }
    }

    /**
     * 校验对象参数非空。
     *
     * @param object 待校验对象
     * @param parameterName 公开参数名称
     */
    private static void requireObject(Object object, String parameterName) {
        if (object == null) {
            throw ReflectionOperationException.invalidArgument(parameterName);
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

    /**
     * 校验并复制精确方法参数类型数组。
     *
     * @param parameterTypes 调用方参数类型数组
     * @return 非空数组副本
     */
    private static Class<?>[] requireParameterTypes(Class<?>[] parameterTypes) {
        if (parameterTypes == null) {
            throw ReflectionOperationException.invalidArgument("parameterTypes");
        }
        Class<?>[] safeTypes = parameterTypes.clone();
        for (Class<?> parameterType : safeTypes) {
            if (parameterType == null) {
                throw ReflectionOperationException.invalidArgument("parameterTypes");
            }
        }
        return safeTypes;
    }

    /**
     * 在 Spring 类型差异算法基础上拒绝同权重重载歧义的方法调用器。
     */
    private static final class StrictMethodInvoker extends MethodInvoker {

        /**
         * 查找权重最小且唯一的方法候选。
         *
         * @return 唯一候选；不存在或存在同权重歧义时返回 {@code null}
         */
        @Override
        protected Method findMatchingMethod() {
            Class<?> targetClass = getTargetClass();
            if (targetClass == null) {
                return null;
            }
            String targetMethod = getTargetMethod();
            Object[] arguments = getArguments();
            int minimumWeight = Integer.MAX_VALUE;
            Method matchingMethod = null;
            boolean ambiguous = false;

            for (Method candidate : ReflectionUtils.getUniqueDeclaredMethods(targetClass)) {
                if (!candidate.getName().equals(targetMethod)
                        || candidate.getParameterCount() != arguments.length) {
                    continue;
                }
                int weight = MethodInvoker.getTypeDifferenceWeight(
                        candidate.getParameterTypes(),
                        arguments
                );
                if (weight < minimumWeight) {
                    minimumWeight = weight;
                    matchingMethod = candidate;
                    ambiguous = false;
                } else if (weight == minimumWeight && weight < Integer.MAX_VALUE) {
                    // 同一权重无法保证调用意图，必须由调用方显式选择 Method。
                    ambiguous = true;
                }
            }
            return ambiguous ? null : matchingMethod;
        }
    }
}

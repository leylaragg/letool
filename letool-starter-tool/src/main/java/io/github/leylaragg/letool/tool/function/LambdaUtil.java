package io.github.leylaragg.letool.tool.function;

import io.github.leylaragg.letool.tool.reflection.ReflectionOperationException;
import org.springframework.util.ReflectionUtils;

import java.beans.Introspector;
import java.io.Serializable;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodType;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.function.Function;

/**
 * 从可序列化方法引用中解析 JavaBean 或 record 属性名的便捷工具。
 *
 * <p>支持无参数且非 void 的实例方法引用：标准 {@code getXxx()}、返回 primitive boolean 的
 * {@code isXxx()}，以及 record 组件访问器。普通 Lambda、静态方法和任意业务方法会被明确拒绝。</p>
 *
 * <p>工具只通过 {@link ClassValue} 缓存 Lambda 类的 {@code writeReplace} 反射入口，每次都解析
 * 当前函数实例，不缓存 {@link SerializedLambda} 或捕获参数，避免长期持有业务对象。</p>
 */
public final class LambdaUtil {

    /**
     * 按 Lambda 生成类缓存序列化入口；ClassValue 不会阻止对应类及其类加载器卸载。
     */
    private static final ClassValue<Method> WRITE_REPLACE_METHODS = new ClassValue<>() {

        /**
         * 查找并开放 Lambda 类的序列化替换方法。
         *
         * @param type Lambda 生成类
         * @return 可调用的序列化替换方法
         */
        @Override
        protected Method computeValue(Class<?> type) {
            Method method = ReflectionUtils.findMethod(type, "writeReplace");
            if (method == null) {
                throw ReflectionOperationException.lambdaResolutionFailed(type.getName(), null);
            }
            try {
                ReflectionUtils.makeAccessible(method);
                return method;
            } catch (RuntimeException exception) {
                throw ReflectionOperationException.lambdaResolutionFailed(
                        type.getName(),
                        exception
                );
            }
        }
    };

    /** 工具类不允许实例化。 */
    private LambdaUtil() {
    }

    /**
     * 从受支持的 Getter 或 record 组件方法引用中提取属性名。
     *
     * @param function 可序列化实例方法引用
     * @param <T> 源对象类型
     * @param <R> 属性值类型
     * @return 符合 JavaBeans 首字母处理规则的属性名
     * @throws ReflectionOperationException 参数无效或方法引用不属于受支持的属性访问器时抛出
     */
    public static <T, R> String getPropertyName(SFunction<T, R> function) {
        if (function == null) {
            throw ReflectionOperationException.invalidArgument("function");
        }
        SerializedLambda lambda = resolve(function);
        String implementation = lambda.getImplClass().replace('/', '.')
                + "#" + lambda.getImplMethodName();

        try {
            ClassLoader classLoader = resolveClassLoader(function.getClass());
            MethodType methodType = MethodType.fromMethodDescriptorString(
                    lambda.getImplMethodSignature(),
                    classLoader
            );
            Class<?> implementationClass = Class.forName(
                    lambda.getImplClass().replace('/', '.'),
                    false,
                    classLoader
            );
            validateInstanceAccessor(lambda, methodType, implementation);
            return resolvePropertyName(
                    implementationClass,
                    lambda.getImplMethodName(),
                    methodType.returnType(),
                    implementation
            );
        } catch (ReflectionOperationException exception) {
            throw exception;
        } catch (ClassNotFoundException | LinkageError | RuntimeException exception) {
            throw ReflectionOperationException.lambdaResolutionFailed(
                    implementation,
                    exception
            );
        }
    }

    /**
     * 解析当前函数实例的 SerializedLambda，不缓存捕获参数。
     *
     * @param function 当前函数实例
     * @return 当前实例的 Lambda 序列化元数据
     */
    private static SerializedLambda resolve(SFunction<?, ?> function) {
        Class<?> lambdaClass = function.getClass();
        try {
            Object resolved = WRITE_REPLACE_METHODS.get(lambdaClass).invoke(function);
            if (resolved instanceof SerializedLambda serializedLambda) {
                return serializedLambda;
            }
            throw ReflectionOperationException.lambdaResolutionFailed(
                    lambdaClass.getName(),
                    new IllegalStateException("writeReplace did not return SerializedLambda")
            );
        } catch (ReflectionOperationException exception) {
            throw exception;
        } catch (InvocationTargetException exception) {
            throw ReflectionOperationException.lambdaResolutionFailed(
                    lambdaClass.getName(),
                    exception.getTargetException()
            );
        } catch (IllegalAccessException | RuntimeException exception) {
            throw ReflectionOperationException.lambdaResolutionFailed(
                    lambdaClass.getName(),
                    exception
            );
        }
    }

    /**
     * 校验实现方法属于无参数、非 void 的实例调用。
     *
     * @param lambda Lambda 序列化元数据
     * @param methodType 实现方法类型
     * @param implementation 安全实现方法标识
     */
    private static void validateInstanceAccessor(
            SerializedLambda lambda,
            MethodType methodType,
            String implementation) {
        int methodKind = lambda.getImplMethodKind();
        boolean instanceInvocation = methodKind == MethodHandleInfo.REF_invokeVirtual
                || methodKind == MethodHandleInfo.REF_invokeInterface
                || methodKind == MethodHandleInfo.REF_invokeSpecial;
        if (!instanceInvocation
                || methodType.parameterCount() != 0
                || methodType.returnType() == void.class
                || lambda.getImplMethodName().startsWith("lambda$")) {
            throw ReflectionOperationException.lambdaResolutionFailed(implementation, null);
        }
    }

    /**
     * 按 JavaBeans 或 record 规则解析属性名称。
     *
     * @param implementationClass 实现方法声明类型
     * @param methodName 实现方法名称
     * @param returnType 实现方法返回类型
     * @param implementation 安全实现方法标识
     * @return 合法属性名称
     */
    private static String resolvePropertyName(
            Class<?> implementationClass,
            String methodName,
            Class<?> returnType,
            String implementation) {
        if (methodName.startsWith("get")
                && methodName.length() > 3
                && Character.isUpperCase(methodName.charAt(3))
                && !(implementationClass == Object.class && methodName.equals("getClass"))) {
            return Introspector.decapitalize(methodName.substring(3));
        }
        if (methodName.startsWith("is")
                && methodName.length() > 2
                && Character.isUpperCase(methodName.charAt(2))
                && returnType == boolean.class) {
            return Introspector.decapitalize(methodName.substring(2));
        }
        if (isRecordComponent(implementationClass, methodName, returnType)) {
            return methodName;
        }
        throw ReflectionOperationException.lambdaResolutionFailed(implementation, null);
    }

    /**
     * 判断方法是否为指定 record 的组件访问器。
     *
     * @param implementationClass 实现方法声明类型
     * @param methodName 方法名称
     * @param returnType 方法返回类型
     * @return 名称和类型均匹配 record 组件时返回 {@code true}
     */
    private static boolean isRecordComponent(
            Class<?> implementationClass,
            String methodName,
            Class<?> returnType) {
        if (!implementationClass.isRecord()) {
            return false;
        }
        for (RecordComponent component : implementationClass.getRecordComponents()) {
            if (component.getName().equals(methodName)
                    && component.getType().equals(returnType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取能够同时看到 Lambda 类和实现类的类加载器。
     *
     * @param lambdaClass Lambda 生成类
     * @return 非空类加载器
     */
    private static ClassLoader resolveClassLoader(Class<?> lambdaClass) {
        ClassLoader classLoader = lambdaClass.getClassLoader();
        if (classLoader == null) {
            classLoader = Thread.currentThread().getContextClassLoader();
        }
        if (classLoader == null) {
            classLoader = LambdaUtil.class.getClassLoader();
        }
        return classLoader;
    }

    /**
     * 可序列化 Function，用于让 JDK 生成可解析的 SerializedLambda 元数据。
     *
     * @param <T> 输入对象类型
     * @param <R> 返回属性类型
     */
    @FunctionalInterface
    public interface SFunction<T, R> extends Function<T, R>, Serializable {
    }
}

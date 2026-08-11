package com.github.leyland.letool.tool.reflection;

import com.github.leyland.letool.exception.core.SystemException;

import java.io.Serial;
import java.util.NoSuchElementException;

/**
 * Bean、反射、类扫描和 Lambda 属性解析失败时抛出的统一异常。
 *
 * <p>公开消息只包含类型名、成员名或包名等安全标识，不记录字段值、方法参数、
 * Lambda 捕获参数或底层异常消息；底层原因保留在异常链中供受控诊断。</p>
 */
public final class ReflectionOperationException extends SystemException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建反射工具统一异常。
     *
     * @param errorCode 稳定错误码
     * @param safeSubject 安全的类型、成员或包标识
     * @param cause 底层失败原因，允许为空
     */
    private ReflectionOperationException(
            ReflectionErrorCode errorCode,
            String safeSubject,
            Throwable cause) {
        super(errorCode, new Object[]{safe(safeSubject)}, null, cause);
    }

    /**
     * 创建参数无效异常。
     *
     * @param parameterName 公开参数名称
     * @return 参数无效异常
     */
    public static ReflectionOperationException invalidArgument(String parameterName) {
        String safeName = safe(parameterName);
        return new ReflectionOperationException(
                ReflectionErrorCode.INVALID_ARGUMENT,
                safeName,
                new IllegalArgumentException("Invalid reflection argument: " + safeName)
        );
    }

    /**
     * 创建成员不存在异常。
     *
     * @param memberName 安全成员标识
     * @return 成员不存在异常
     */
    public static ReflectionOperationException memberNotFound(String memberName) {
        String safeName = safe(memberName);
        return new ReflectionOperationException(
                ReflectionErrorCode.MEMBER_NOT_FOUND,
                safeName,
                new NoSuchElementException("Reflection member not found: " + safeName)
        );
    }

    /**
     * 创建字段或 Bean 属性访问异常。
     *
     * @param fieldName 安全字段或属性标识
     * @param cause 底层失败原因
     * @return 字段访问异常
     */
    public static ReflectionOperationException fieldAccessFailed(
            String fieldName,
            Throwable cause) {
        return new ReflectionOperationException(
                ReflectionErrorCode.FIELD_ACCESS_FAILED,
                fieldName,
                cause
        );
    }

    /**
     * 创建方法调用异常。
     *
     * @param methodName 安全方法标识
     * @param cause 底层失败原因
     * @return 方法调用异常
     */
    public static ReflectionOperationException methodInvocationFailed(
            String methodName,
            Throwable cause) {
        return new ReflectionOperationException(
                ReflectionErrorCode.METHOD_INVOCATION_FAILED,
                methodName,
                cause
        );
    }

    /**
     * 创建类型实例化异常。
     *
     * @param type 目标类型
     * @param cause 底层失败原因
     * @return 实例化异常
     */
    public static ReflectionOperationException instantiationFailed(
            Class<?> type,
            Throwable cause) {
        return new ReflectionOperationException(
                ReflectionErrorCode.INSTANTIATION_FAILED,
                type == null ? "unknown" : type.getName(),
                cause
        );
    }

    /**
     * 创建类路径扫描或类加载异常。
     *
     * @param scanScope 安全扫描包名或类名
     * @param cause 底层失败原因
     * @return 类扫描异常
     */
    public static ReflectionOperationException classScanFailed(
            String scanScope,
            Throwable cause) {
        return new ReflectionOperationException(
                ReflectionErrorCode.CLASS_SCAN_FAILED,
                scanScope,
                cause
        );
    }

    /**
     * 创建 Lambda 属性解析异常。
     *
     * @param lambdaType Lambda 类型或实现方法标识
     * @param cause 底层失败原因，允许为空
     * @return Lambda 解析异常
     */
    public static ReflectionOperationException lambdaResolutionFailed(
            String lambdaType,
            Throwable cause) {
        return new ReflectionOperationException(
                ReflectionErrorCode.LAMBDA_RESOLUTION_FAILED,
                lambdaType,
                cause
        );
    }

    /**
     * 规范化公开消息中的安全标识。
     *
     * @param value 待规范化标识
     * @return 非空且非空白的安全标识
     */
    private static String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}

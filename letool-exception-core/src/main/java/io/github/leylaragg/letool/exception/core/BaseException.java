package io.github.leylaragg.letool.exception.core;

import io.github.leylaragg.letool.exception.code.ErrorCode;
import io.github.leylaragg.letool.exception.support.MessageFormatter;

import java.io.Serial;
import java.util.Locale;

/**
 * 支持将 HTTP 国际化延迟到响应边界处理的错误码异常基类。
 *
 * <p>异常会保留错误码和防御性复制后的消息参数，供 HTTP 适配器按请求语言环境解析。
 * 构造时还会生成不依赖请求或应用上下文的稳定默认消息，确保后台任务和日志始终可读。
 * 将异常作为 {@link Throwable} 记录时，会完整保留带错误码的消息、堆栈和异常原因链，
 * 比只记录消息文本更便于排障。</p>
 *
 * <p>进行 Java 序列化时，错误码、保留的消息参数以及完整异常原因链都必须支持序列化。
 * 消息参数会保留原始类型而不是提前转成字符串，使延迟国际化仍能按语言环境格式化数字和日期。</p>
 */
public abstract class BaseException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用于响应映射和稳定日志标识的结构化错误码。 */
    private final ErrorCode errorCode;

    /** 为响应边界国际化解析而保留的消息参数防御性副本。 */
    private final Object[] messageArgs;

    /** 跳过资源包查找的显式消息；仍需国际化时为 {@code null}。 */
    private final String customMessage;

    /** 构造时生成的稳定消息快照，日志使用它时不依赖语言环境或应用上下文。 */
    private final String fallbackMessage;

    /**
     * 为扩展类型创建带错误码的异常快照。
     *
     * <p>消息参数数组会在格式化和保存前复制。非 {@code null} 的自定义消息会主动跳过
     * 国际化格式化；否则使用 {@link Locale#ROOT} 格式化错误码的默认模板。
     * 底层异常通过标准 Throwable 链保留。</p>
     *
     * @param errorCode 必填的错误码和默认消息模板来源
     * @param messageArgs 国际化模板参数；没有参数时可传 {@code null}
     * @param customMessage 不参与国际化的显式消息；使用默认模板时传 {@code null}
     * @param cause 底层异常；没有底层异常时可传 {@code null}
     * @throws IllegalArgumentException 当错误码或其标识不合法时抛出
     */
    protected BaseException(
            ErrorCode errorCode,
            Object[] messageArgs,
            String customMessage,
            Throwable cause) {
        this(PreparedState.prepare(errorCode, messageArgs, customMessage), cause);
    }

    private BaseException(PreparedState state, Throwable cause) {
        super(state.throwableMessage(), cause);
        this.errorCode = state.errorCode();
        this.messageArgs = state.messageArgs();
        this.customMessage = state.customMessage();
        this.fallbackMessage = state.fallbackMessage();
    }

    /**
     * 获取构造当前异常时使用的结构化错误码。
     *
     * @return 按接口约定不可变的错误码定义
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * 获取适用于日志和响应协议的稳定标识。
     *
     * @return 错误码标识
     */
    public String getCode() {
        return errorCode.getCode();
    }

    /**
     * 获取为延迟国际化保留的消息参数防御性副本。
     *
     * @return 包含原始参数引用的新数组
     */
    public Object[] getMessageArgs() {
        return messageArgs.clone();
    }

    /**
     * 获取显式指定且不参与国际化的消息。
     *
     * @return 自定义消息；使用错误码默认模板时返回 {@code null}
     */
    public String getCustomMessage() {
        return customMessage;
    }

    /**
     * 获取异常构造时生成的稳定消息。
     *
     * @return 自定义消息，或使用根语言环境格式化后的默认消息
     */
    public String getFallbackMessage() {
        return fallbackMessage;
    }

    /**
     * 判断是否通过显式文本主动跳过国际化。
     *
     * @return 提供了非 {@code null} 自定义消息时返回 {@code true}
     */
    public boolean hasCustomMessage() {
        return customMessage != null;
    }

    /**
     * 输出运行时类型和稳定的带码消息，不重新计算国际化内容。
     *
     * @return 运行时类全限定名和异常消息
     */
    @Override
    public String toString() {
        return getClass().getName() + ": " + getMessage();
    }

    private record PreparedState(
            ErrorCode errorCode,
            Object[] messageArgs,
            String customMessage,
            String fallbackMessage,
            String throwableMessage) {

        private static PreparedState prepare(
                ErrorCode errorCode,
                Object[] messageArgs,
                String customMessage) {
            ErrorCode validatedCode = requireErrorCode(errorCode);
            Object[] safeArguments = messageArgs == null ? new Object[0] : messageArgs.clone();
            String fallback = customMessage != null
                    ? customMessage
                    : MessageFormatter.format(
                            validatedCode.getDefaultMessage(),
                            Locale.ROOT,
                            safeArguments);
            String throwableMessage = "[" + validatedCode.getCode() + "] " + fallback;
            return new PreparedState(
                    validatedCode,
                    safeArguments,
                    customMessage,
                    fallback,
                    throwableMessage);
        }

        private static ErrorCode requireErrorCode(ErrorCode errorCode) {
            if (errorCode == null) {
                throw new IllegalArgumentException("errorCode must not be null");
            }
            if (errorCode.getCode() == null || errorCode.getCode().isBlank()) {
                throw new IllegalArgumentException("errorCode.code must not be blank");
            }
            return errorCode;
        }
    }
}

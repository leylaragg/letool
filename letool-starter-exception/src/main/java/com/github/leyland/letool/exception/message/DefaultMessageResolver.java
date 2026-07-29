package com.github.leyland.letool.exception.message;

import com.github.leyland.letool.exception.code.ErrorCode;
import com.github.leyland.letool.exception.core.BaseException;
import com.github.leyland.letool.exception.support.MessageFormatter;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;
import java.util.Objects;

/**
 * 不依赖消息资源包，直接根据错误码默认模板解析消息。
 *
 * <p>配置的语言环境不可变，并且只从 Spring 线程绑定上下文读取请求语言环境，
 * 因此该类是线程安全的。</p>
 */
public final class DefaultMessageResolver implements MessageResolver {

    private final Locale defaultLocale;

    /**
     * 创建带固定默认语言环境的兜底解析器，供脱离国际化请求上下文时使用。
     *
     * @param defaultLocale 未显式指定且线程上下文也没有语言环境时使用的必填默认值
     * @throws NullPointerException 当 {@code defaultLocale} 为 {@code null} 时抛出
     */
    public DefaultMessageResolver(Locale defaultLocale) {
        this.defaultLocale = Objects.requireNonNull(defaultLocale, "defaultLocale");
    }

    /**
     * 使用请求绑定语言环境或解析器默认语言环境解析异常。
     *
     * @param exception 必填的带错误码异常
     * @return 面向用户的格式化文本，不包含仅供日志使用的错误码前缀
     * @throws NullPointerException 当 {@code exception} 为 {@code null} 时抛出
     */
    @Override
    public String resolve(BaseException exception) {
        Objects.requireNonNull(exception, "exception");
        LocaleContext localeContext = LocaleContextHolder.getLocaleContext();
        // 直接读取绑定上下文，避免在没有请求语言环境时被 JVM 默认语言环境静默替代。
        Locale locale = localeContext == null ? defaultLocale : localeContext.getLocale();
        return resolve(exception, locale);
    }

    /**
     * 按指定语言环境解析异常，并原样保留显式自定义消息。
     *
     * @param exception 必填的带错误码异常
     * @param locale 指定语言环境；传 {@code null} 时使用配置的默认值
     * @return 面向用户的格式化文本，不包含仅供日志使用的错误码前缀
     * @throws NullPointerException 当 {@code exception} 为 {@code null} 时抛出
     */
    @Override
    public String resolve(BaseException exception, Locale locale) {
        Objects.requireNonNull(exception, "exception");
        if (exception.hasCustomMessage()) {
            return exception.getCustomMessage();
        }
        return resolve(
                exception.getErrorCode(),
                effectiveLocale(locale),
                exception.getMessageArgs());
    }

    /**
     * 按指定语言环境格式化错误码默认消息模板。
     *
     * @param errorCode 必填的错误码定义
     * @param locale 指定语言环境；传 {@code null} 时使用配置的默认值
     * @param args 填充默认消息模板的参数
     * @return 面向用户的格式化文本，不包含诊断错误码前缀
     * @throws NullPointerException 当 {@code errorCode} 为 {@code null} 时抛出
     */
    @Override
    public String resolve(ErrorCode errorCode, Locale locale, Object... args) {
        Objects.requireNonNull(errorCode, "errorCode");
        return MessageFormatter.format(
                errorCode.getDefaultMessage(),
                effectiveLocale(locale),
                args);
    }

    private Locale effectiveLocale(Locale locale) {
        return locale == null ? defaultLocale : locale;
    }
}

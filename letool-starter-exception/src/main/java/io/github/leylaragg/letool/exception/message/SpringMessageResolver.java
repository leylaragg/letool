package io.github.leylaragg.letool.exception.message;

import io.github.leylaragg.letool.exception.code.ErrorCode;
import io.github.leylaragg.letool.exception.core.BaseException;
import io.github.leylaragg.letool.exception.support.MessageFormatter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;
import java.util.Objects;

/**
 * 按“应用消息源、Starter 消息源、稳定默认消息”的顺序解析异常消息。
 *
 * <p>格式错误的 {@link java.text.MessageFormat} 模板会被视为当前消息源未命中，
 * 并继续进入正常回退链。</p>
 *
 * <p>解析过程无状态；只要注入的 {@link MessageSource} 实现支持并发访问，本类就是线程安全的。</p>
 */
public final class SpringMessageResolver implements MessageResolver {

    private static final Log log = LogFactory.getLog(SpringMessageResolver.class);
    private static final String MESSAGE_NOT_FOUND = "\u0000letool-message-not-found\u0000";

    private final MessageSource applicationMessageSource;
    private final MessageSource starterMessageSource;
    private final Locale defaultLocale;

    /**
     * 创建应用资源优先于 Starter 资源的消息解析器。
     *
     * @param applicationMessageSource 可选的应用消息源，用于覆盖框架消息
     * @param starterMessageSource 必填的 Starter 消息源
     * @param defaultLocale 未显式指定且线程上下文也没有语言环境时使用的必填默认值
     * @throws NullPointerException 当 {@code starterMessageSource} 或
     *         {@code defaultLocale} 为 {@code null} 时抛出
     */
    public SpringMessageResolver(
            MessageSource applicationMessageSource,
            MessageSource starterMessageSource,
            Locale defaultLocale) {
        this.applicationMessageSource = applicationMessageSource;
        this.starterMessageSource =
                Objects.requireNonNull(starterMessageSource, "starterMessageSource");
        this.defaultLocale = Objects.requireNonNull(defaultLocale, "defaultLocale");
    }

    /**
     * 使用请求绑定语言环境或解析器默认语言环境解析异常。
     *
     * @param exception 必填的带错误码异常
     * @return 面向用户的国际化文本，不包含仅供日志使用的错误码前缀
     * @throws NullPointerException 当 {@code exception} 为 {@code null} 时抛出
     */
    @Override
    public String resolve(BaseException exception) {
        Objects.requireNonNull(exception, "exception");
        LocaleContext localeContext = LocaleContextHolder.getLocaleContext();
        Locale locale = localeContext == null ? defaultLocale : localeContext.getLocale();
        return resolve(exception, locale);
    }

    /**
     * 按指定语言环境解析异常，并原样保留显式自定义消息。
     *
     * @param exception 必填的带错误码异常
     * @param locale 指定语言环境；传 {@code null} 时使用配置的默认值
     * @return 面向用户的国际化文本，不包含仅供日志使用的错误码前缀
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
     * 按应用消息源、Starter 消息源、错误码默认模板的顺序解析错误码。
     *
     * @param errorCode 必填的错误码定义
     * @param locale 指定语言环境；传 {@code null} 时使用配置的默认值
     * @param args 填充最终消息模板的参数
     * @return 不含诊断错误码前缀的面向用户国际化文本
     * @throws NullPointerException 当 {@code errorCode} 为 {@code null} 时抛出
     */
    @Override
    public String resolve(ErrorCode errorCode, Locale locale, Object... args) {
        Objects.requireNonNull(errorCode, "errorCode");
        Locale effectiveLocale = effectiveLocale(locale);
        Object[] safeArguments = args == null ? new Object[0] : args.clone();

        // 应用消息优先，使业务服务无需替换解析器或重新打包 Starter 就能自定义文案。
        String message =
                find(
                        applicationMessageSource,
                        "application",
                        errorCode.getCode(),
                        effectiveLocale,
                        safeArguments);
        if (message != null) {
            return message;
        }

        message =
                find(
                        starterMessageSource,
                        "starter",
                        errorCode.getCode(),
                        effectiveLocale,
                        safeArguments);
        if (message != null) {
            return message;
        }

        return MessageFormatter.format(
                errorCode.getDefaultMessage(),
                effectiveLocale,
                safeArguments);
    }

    private String find(
            MessageSource messageSource,
            String source,
            String code,
            Locale locale,
            Object[] args) {
        if (messageSource == null) {
            return null;
        }
        // 使用非 null 哨兵值，避免 useCodeAsDefaultMessage 将未命中结果直接变成错误码。
        try {
            String message =
                    messageSource.getMessage(code, args, MESSAGE_NOT_FOUND, locale);
            return MESSAGE_NOT_FOUND.equals(message) ? null : message;
        } catch (IllegalArgumentException exception) {
            // 消息参数和模板可能包含敏感信息，因此诊断日志只记录来源与查找键，然后继续安全回退。
            log.warn(
                    "Ignoring malformed exception message template: source="
                            + source
                            + ", code="
                            + code
                            + ", locale="
                            + locale);
            return null;
        }
    }

    private Locale effectiveLocale(Locale locale) {
        return locale == null ? defaultLocale : locale;
    }
}

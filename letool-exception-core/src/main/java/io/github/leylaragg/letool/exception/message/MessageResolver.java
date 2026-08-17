package io.github.leylaragg.letool.exception.message;

import io.github.leylaragg.letool.exception.code.ErrorCode;
import io.github.leylaragg.letool.exception.core.BaseException;

import java.util.Locale;

/**
 * 将带错误码异常解析为面向用户的国际化消息。
 *
 * <p>返回值只包含展示文本，不包含 {@link BaseException#getMessage()} 中仅供日志使用的
 * {@code [CODE]} 前缀。</p>
 */
public interface MessageResolver {

    /**
     * 存在请求绑定语言环境时使用该环境解析异常，否则使用解析器配置的默认语言环境。
     *
     * @param exception 必填的带错误码异常
     * @return 不含诊断错误码前缀的面向用户国际化文本
     */
    String resolve(BaseException exception);

    /**
     * 按显式指定的语言环境解析异常。
     *
     * @param exception 必填的带错误码异常
     * @param locale 指定语言环境；传 {@code null} 时使用解析器默认值
     * @return 不含诊断错误码前缀的面向用户国际化文本
     */
    String resolve(BaseException exception, Locale locale);

    /**
     * 按显式指定的语言环境解析错误码及其格式化参数。
     *
     * @param errorCode 必填的错误码定义
     * @param locale 指定语言环境；传 {@code null} 时使用解析器默认值
     * @param args 填充最终消息模板的参数
     * @return 不含诊断错误码前缀的面向用户国际化文本
     */
    String resolve(ErrorCode errorCode, Locale locale, Object... args);
}

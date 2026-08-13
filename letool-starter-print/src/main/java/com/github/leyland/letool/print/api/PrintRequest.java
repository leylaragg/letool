package com.github.leyland.letool.print.api;

import com.github.leyland.letool.print.context.PrintContext;
import com.github.leyland.letool.print.exception.PrintValidationException;

import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;

/**
 * 单次同步打印所需的不可变请求。
 *
 * <p>请求只包含已锁定模板与只读上下文，不包含数据库、业务 Service 或认证信息。</p>
 *
 * @author leyland
 */
public final class PrintRequest {

    /** 本次打印锁定的模板快照。 */
    private final PrintTemplate template;

    /** 已完成业务转换的只读上下文。 */
    private final PrintContext context;

    /** 请求的产物格式。 */
    private final OutputFormat outputFormat;

    /** 文本格式化区域。 */
    private final Locale locale;

    /** 日期时间格式化时区。 */
    private final ZoneId zoneId;

    /** 通用渲染限制。 */
    private final RenderOptions options;

    /**
     * 创建同步打印请求并校验模板与上下文版本。
     *
     * @param template 本次打印锁定的模板快照
     * @param context 只读打印上下文
     * @param outputFormat 请求的产物格式
     * @param locale 文本格式化区域
     * @param zoneId 日期时间格式化时区
     * @param options 通用渲染限制
     * @throws NullPointerException 任一必填值为 {@code null} 时抛出
     * @throws PrintValidationException 模板要求的上下文版本与实际版本不一致时抛出
     */
    public PrintRequest(PrintTemplate template, PrintContext context, OutputFormat outputFormat,
                        Locale locale, ZoneId zoneId, RenderOptions options) {
        this.template = Objects.requireNonNull(template, "template 不能为空");
        this.context = Objects.requireNonNull(context, "context 不能为空");
        this.outputFormat = Objects.requireNonNull(outputFormat, "outputFormat 不能为空");
        this.locale = Objects.requireNonNull(locale, "locale 不能为空");
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId 不能为空");
        this.options = Objects.requireNonNull(options, "options 不能为空");
        if (template.contextVersion() != context.version()) {
            throw PrintValidationException.invalidRequest(
                    "模板上下文版本与请求上下文版本不一致");
        }
    }

    /** @return 本次打印锁定的模板快照 */
    public PrintTemplate template() {
        return template;
    }

    /** @return 只读打印上下文 */
    public PrintContext context() {
        return context;
    }

    /** @return 请求的产物格式 */
    public OutputFormat outputFormat() {
        return outputFormat;
    }

    /** @return 文本格式化区域 */
    public Locale locale() {
        return locale;
    }

    /** @return 日期时间格式化时区 */
    public ZoneId zoneId() {
        return zoneId;
    }

    /** @return 通用渲染限制 */
    public RenderOptions options() {
        return options;
    }
}

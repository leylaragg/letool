package io.github.leylaragg.letool.print.xml.format;

import java.util.List;

/**
 * Letool XML 默认启用的内置格式化器集合。
 *
 * @author leyland
 */
public final class BuiltInPrintFormatters {

    /** 可与宿主扩展合并的不可变内置实例。 */
    private static final List<PrintValueFormatter> FORMATTERS = List.of(
            new NumberValueFormatter(),
            new TemporalValueFormatter("date", false),
            new TemporalValueFormatter("datetime", true),
            new BooleanValueFormatter(),
            new JoinValueFormatter());

    /** 进程内可安全复用的不可变默认注册表。 */
    private static final PrintFormatterRegistry REGISTRY = new PrintFormatterRegistry(FORMATTERS);

    /** 禁止实例化工具类。 */
    private BuiltInPrintFormatters() {
    }

    /**
     * 返回默认格式化器注册表。
     *
     * @return 不可变默认注册表
     */
    public static PrintFormatterRegistry registry() {
        return REGISTRY;
    }

    /**
     * 返回供宿主组合扩展的内置格式化器快照。
     *
     * @return 保持默认顺序的不可修改列表
     */
    public static List<PrintValueFormatter> formatters() {
        return FORMATTERS;
    }
}

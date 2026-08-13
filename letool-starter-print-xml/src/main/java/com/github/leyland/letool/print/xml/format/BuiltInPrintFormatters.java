package com.github.leyland.letool.print.xml.format;

import java.util.List;

/**
 * Letool XML 默认启用的内置格式化器集合。
 *
 * @author leyland
 */
public final class BuiltInPrintFormatters {

    /** 进程内可安全复用的不可变默认注册表。 */
    private static final PrintFormatterRegistry REGISTRY = new PrintFormatterRegistry(List.of(
            new NumberValueFormatter(),
            new TemporalValueFormatter("date", false),
            new TemporalValueFormatter("datetime", true)));

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
}

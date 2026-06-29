package com.github.leyland.letool.excel.convert;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 日期字符串转换器。
 *
 * <p>实现 {@link ExcelConverter}{@code <LocalDate>}，将Excel单元格中的
 * 日期字符串与 {@link LocalDate} 之间进行双向转换。
 *
 * <p>通过构造函数传入日期格式化模式（如 {@code "yyyy-MM-dd"}），
 * 内部使用 {@link DateTimeFormatter} 进行解析和格式化。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 在配置类中注册
 * @Bean("dateConverter")
 * public ExcelConverter<LocalDate> dateConverter() {
 *     return new DateConverter("yyyy-MM-dd");
 * }
 *
 * // 在实体类中使用
 * @ExcelColumn(value = "生日", converter = "dateConverter")
 * private LocalDate birthday;
 * }</pre>
 *
 * @author leyland
 * @since 1.0.0
 */
public class DateConverter implements ExcelConverter<LocalDate> {

    // ======================== 日期格式化器 ========================

    /**
     * 基于指定模式的日期格式化器，用于解析和格式化日期字符串。
     */
    private final DateTimeFormatter formatter;

    // ======================== 构造方法 ========================

    /**
     * 使用指定的日期模式创建转换器。
     *
     * @param pattern 日期格式化模式，如 {@code "yyyy-MM-dd HH:mm:ss"}，
     *                遵循 {@link DateTimeFormatter} 的格式规范
     */
    public DateConverter(String pattern) {
        this.formatter = DateTimeFormatter.ofPattern(pattern);
    }

    // ======================== Excel -> Java 转换 ========================

    /**
     * 将Excel单元格中的日期字符串解析为 {@link LocalDate}。
     *
     * <p>当单元格值为 {@code null} 或空字符串时，返回 {@code null}。
     *
     * @param excelValue 日期字符串，不可为 {@code null}
     * @return 解析后的 {@link LocalDate}，若输入为空则返回 {@code null}
     * @throws java.time.format.DateTimeParseException 当字符串格式与模式不匹配时抛出
     */
    @Override
    public LocalDate convertToJava(String excelValue) {
        return excelValue == null || excelValue.isEmpty() ? null : LocalDate.parse(excelValue, formatter);
    }

    // ======================== Java -> Excel 转换 ========================

    /**
     * 将 {@link LocalDate} 格式化为Excel单元格中的日期字符串。
     *
     * <p>当值为 {@code null} 时，返回空字符串。
     *
     * @param javaValue 要格式化的日期对象，可能为 {@code null}
     * @return 格式化后的日期字符串；若输入为 {@code null} 则返回空字符串
     */
    @Override
    public String convertToExcel(LocalDate javaValue) {
        return javaValue == null ? "" : formatter.format(javaValue);
    }
}

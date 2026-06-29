package com.github.leyland.letool.excel.convert;

/**
 * Excel值与Java对象之间的类型转换接口。
 *
 * <p>当EasyExcel内置的类型转换无法满足需求时，可实现此接口
 * 并通过 {@code @ExcelColumn(converter = "beanName")} 指定。
 *
 * <p>转换是双向的：
 * <ul>
 *   <li>{@link #convertToJava(String)} —— 读取Excel时，将单元格字符串转为Java对象</li>
 *   <li>{@link #convertToExcel(T)} —— 写入Excel时，将Java对象转为单元格字符串</li>
 * </ul>
 *
 * <p>内置实现：
 * <ul>
 *   <li>{@link DateConverter} —— 日期字符串转换</li>
 *   <li>{@link EnumConverter} —— 枚举名称匹配转换</li>
 * </ul>
 *
 * @param <T> 目标Java类型
 * @author leyland
 * @since 1.0.0
 */
public interface ExcelConverter<T> {

    // ======================== Excel -> Java 转换 ========================

    /**
     * 将Excel单元格中的字符串值转换为目标Java对象。
     *
     * <p>在读取（导入）Excel时调用。实现类应处理
     * {@code null} 和空字符串的情况。
     *
     * @param excelValue Excel单元格中的原始字符串值，可能为{@code null}或空
     * @return 转换后的Java对象；若无法转换可抛出异常或返回{@code null}
     * @throws IllegalArgumentException 当字符串值无法识别时抛出
     */
    T convertToJava(String excelValue);

    // ======================== Java -> Excel 转换 ========================

    /**
     * 将Java对象转换为写入Excel单元格的字符串。
     *
     * <p>在写入（导出）Excel时调用。实现类应处理
     * {@code null} 值的情况，通常返回空字符串。
     *
     * @param javaValue 要转换的Java对象，可能为{@code null}
     * @return 用于写入Excel单元格的字符串；若为{@code null}则返回空字符串
     */
    String convertToExcel(T javaValue);
}

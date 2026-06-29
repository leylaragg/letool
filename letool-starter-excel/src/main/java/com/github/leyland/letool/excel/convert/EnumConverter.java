package com.github.leyland.letool.excel.convert;

/**
 * 枚举值转换器。
 *
 * <p>实现 {@link ExcelConverter}{@code <T>}，将Excel单元格中的字符串
 * 与Java枚举常量之间进行双向转换。匹配规则为：
 *
 * <p><b>读取（Excel -> Java）：</b>
 * 忽略大小写进行枚举名称匹配。例如Excel值为 {@code "active"}，
 * 将匹配枚举常量 {@code ACTIVE}。若未找到匹配项，抛出
 * {@link IllegalArgumentException}。
 *
 * <p><b>写入（Java -> Excel）：</b>
 * 直接调用枚举常量的 {@link Enum#name()} 方法，返回大写名称。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 在配置类中注册
 * @Bean("statusConverter")
 * public ExcelConverter<StatusEnum> statusConverter() {
 *     return new EnumConverter<>(StatusEnum.class);
 * }
 *
 * // 在实体类中使用
 * @ExcelColumn(value = "状态", converter = "statusConverter")
 * private StatusEnum status;
 * }</pre>
 *
 * @param <T> 枚举类型，必须继承自 {@link Enum}
 * @author leyland
 * @since 1.0.0
 */
public class EnumConverter<T extends Enum<T>> implements ExcelConverter<T> {

    // ======================== 枚举类型 ========================

    /**
     * 目标枚举的 {@link Class} 对象，用于获取所有枚举常量。
     */
    private final Class<T> enumType;

    // ======================== 构造方法 ========================

    /**
     * 使用指定的枚举类型创建转换器。
     *
     * @param enumType 目标枚举的Class对象，不能为 {@code null}
     */
    public EnumConverter(Class<T> enumType) {
        this.enumType = enumType;
    }

    // ======================== Excel -> Java 转换 ========================

    /**
     * 将Excel单元格字符串转换为对应的枚举常量。
     *
     * <p>通过 {@link Enum#name()} 进行忽略大小写的匹配。
     * 输入字符串会先去除首尾空白字符。
     *
     * @param excelValue Excel单元格中的字符串值，可能为{@code null}或空
     * @return 匹配的枚举常量；若输入为空则返回 {@code null}
     * @throws IllegalArgumentException 当字符串值无法匹配任何枚举常量时抛出
     */
    @Override
    public T convertToJava(String excelValue) {
        if (excelValue == null || excelValue.isEmpty()) return null;
        for (T constant : enumType.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(excelValue.trim())) {
                return constant;
            }
        }
        throw new IllegalArgumentException("Unknown enum value: " + excelValue + " for " + enumType.getName());
    }

    // ======================== Java -> Excel 转换 ========================

    /**
     * 将枚举常量转换为写入Excel单元格的字符串。
     *
     * <p>直接返回枚举常量的 {@link Enum#name()} 值（大写名称）。
     * 当枚举值为 {@code null} 时返回空字符串。
     *
     * @param javaValue 要转换的枚举常量，可能为 {@code null}
     * @return 枚举名称字符串；若输入为 {@code null} 则返回空字符串
     */
    @Override
    public String convertToExcel(T javaValue) {
        return javaValue == null ? "" : javaValue.name();
    }
}

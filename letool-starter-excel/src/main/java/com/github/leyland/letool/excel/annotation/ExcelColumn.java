package com.github.leyland.letool.excel.annotation;

import java.lang.annotation.*;

/**
 * Excel列映射注解。
 *
 * <p>标注在实体类的字段上，用于指定该字段与Excel列的对应关系，
 * 包括列头名称、列索引位置、列宽、日期/数字格式化模式以及自定义转换器。
 *
 * <p>该注解在运行时通过反射读取，由 EasyExcel 框架或 {@code ExcelUtil}
 * 工具类解析使用。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * public class UserDto {
 *     @ExcelColumn("用户名")
 *     private String name;
 *
 *     @ExcelColumn(value = "出生日期", index = 3, format = "yyyy-MM-dd")
 *     private LocalDate birthday;
 *
 *     @ExcelColumn(value = "状态", converter = "statusConverter")
 *     private String status;
 * }
 * }</pre>
 *
 * @author leyland
 * @since 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ExcelColumn {

    // ======================== 列头名称 ========================

    /**
     * Excel列头名称（即表头文字）。
     *
     * <p>当值为空字符串时，框架通常使用字段名作为列头。
     *
     * @return 列头名称，默认为空字符串
     */
    String value() default "";

    // ======================== 列索引位置 ========================

    /**
     * Excel列的索引位置（从0开始）。
     *
     * <p>用于精确控制列的顺序。值为 -1 表示不指定，
     * 由框架按字段声明顺序自动分配。
     *
     * @return 列索引，默认为 -1（自动分配）
     */
    int index() default -1;

    // ======================== 列宽 ========================

    /**
     * Excel列的宽度（单位：字符数）。
     *
     * <p>值为 -1 表示不指定，使用 EasyExcel 的默认列宽
     * 或自动列宽策略。
     *
     * @return 列宽，默认为 -1（自动）
     */
    int width() default -1;

    // ======================== 格式化模式 ========================

    /**
     * 日期或数字的格式化模式。
     *
     * <p>例如：
     * <ul>
     *   <li>{@code "yyyy-MM-dd HH:mm:ss"} —— 日期时间格式</li>
     *   <li>{@code "0.00"} —— 保留两位小数的数字格式</li>
     * </ul>
     * 值为空字符串表示不进行格式化。
     *
     * @return 格式化模式字符串，默认为空
     */
    String format() default "";

    // ======================== 自定义转换器 ========================

    /**
     * 自定义转换器的 Spring Bean 名称。
     *
     * <p>当字段类型无法通过默认规则与Excel单元格值互转时，
     * 可指定一个实现了对应转换接口的 Bean 来完成转换。
     *
     * @return 转换器Bean名称，默认为空字符串
     */
    String converter() default "";
}

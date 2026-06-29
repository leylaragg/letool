package com.github.leyland.letool.excel.validation;

import com.github.leyland.letool.excel.annotation.ExcelValidation;

import java.lang.reflect.Field;
import java.util.regex.Pattern;

/**
 * Excel数据校验器。
 *
 * <p>通过反射机制扫描实体类字段上的 {@link ExcelValidation} 注解，
 * 并根据注解配置的规则对字段值逐个进行校验。支持的校验规则包括：
 * <ul>
 *   <li><b>必填校验</b> —— {@code required}</li>
 *   <li><b>最小长度</b> —— {@code minLength}</li>
 *   <li><b>最大长度</b> —— {@code maxLength}</li>
 *   <li><b>正则匹配</b> —— {@code regex}</li>
 * </ul>
 *
 * <p>多条规则同时生效，任何一条不满足都会产生错误记录。
 * 该类无状态，所有方法均为静态方法，线程安全。
 *
 * @author leyland
 * @since 1.0.0
 */
public class DataValidator {

    // ======================== 单行校验 ========================

    /**
     * 对单个实体对象进行数据校验。
     *
     * <p>使用反射遍历实体类的所有声明字段，查找标注了
     * {@link ExcelValidation} 的字段，并依据注解配置执行校验规则。
     * 通过 {@link Field#setAccessible} 绕过访问控制读取字段值。
     *
     * <p><b>校验流程：</b>
     * <ol>
     *   <li>遍历 {@code entity.getClass().getDeclaredFields()}</li>
     *   <li>检查字段是否有 {@code @ExcelValidation} 注解，无则跳过</li>
     *   <li>反射获取字段值并转为字符串</li>
     *   <li>依次执行 required、minLength、maxLength、regex 校验</li>
     *   <li>任何校验失败，将错误信息添加到结果集中</li>
     * </ol>
     *
     * @param <T>    实体类型
     * @param entity 待校验的实体对象，不能为 {@code null}
     * @param rowNum Excel中的行号，用于错误定位
     * @return 校验结果容器，包含所有校验失败的错误信息
     */
    public static <T> ValidationResult validate(T entity, int rowNum) {
        ValidationResult result = new ValidationResult();
        for (Field field : entity.getClass().getDeclaredFields()) {
            ExcelValidation ann = field.getAnnotation(ExcelValidation.class);
            if (ann == null) continue;
            field.setAccessible(true);
            try {
                Object value = field.get(entity);
                String strValue = value == null ? null : value.toString();

                // —— 必填校验 ——
                if (ann.required() && (strValue == null || strValue.isEmpty())) {
                    result.addError(rowNum, field.getName(), ann.message().isEmpty()
                            ? field.getName() + " is required" : ann.message());
                }
                // —— 最小长度校验 ——
                if (strValue != null && ann.minLength() >= 0 && strValue.length() < ann.minLength()) {
                    result.addError(rowNum, field.getName(), "min length: " + ann.minLength());
                }
                // —— 最大长度校验 ——
                if (strValue != null && ann.maxLength() >= 0 && strValue.length() > ann.maxLength()) {
                    result.addError(rowNum, field.getName(), "max length: " + ann.maxLength());
                }
                // —— 正则表达式校验 ——
                if (strValue != null && !ann.regex().isEmpty()
                        && !Pattern.matches(ann.regex(), strValue)) {
                    result.addError(rowNum, field.getName(),
                            ann.message().isEmpty() ? "does not match pattern" : ann.message());
                }
            } catch (IllegalAccessException ignored) {
                // 理论上不会发生，因为已通过 setAccessible(true) 绕过访问控制
            }
        }
        return result;
    }
}

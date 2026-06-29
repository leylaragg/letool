package com.github.leyland.letool.excel.annotation;

import java.lang.annotation.*;

/**
 * Excel数据校验注解。
 *
 * <p>标注在实体类字段上，用于定义该字段在Excel导入时的数据校验规则。
 * 支持必填校验、长度范围校验、正则表达式校验等多种规则。
 * 校验逻辑由 {@link com.github.leyland.letool.excel.validation.DataValidator}
 * 在导入过程中统一执行。
 *
 * <p>多个校验规则可同时生效，任何一个规则不满足都会产生一条错误信息。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * public class UserDto {
 *     @ExcelValidation(required = true, message = "用户名不能为空")
 *     private String username;
 *
 *     @ExcelValidation(minLength = 6, maxLength = 20, message = "密码长度需在6-20之间")
 *     private String password;
 *
 *     @ExcelValidation(regex = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
 *     private String phone;
 * }
 * }</pre>
 *
 * @author leyland
 * @since 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ExcelValidation {

    // ======================== 必填校验 ========================

    /**
     * 是否必填。
     *
     * <p>当设置为 {@code true} 时，如果单元格值为 {@code null}
     * 或空字符串，将产生校验错误。
     *
     * @return 是否必填，默认 {@code false}
     */
    boolean required() default false;

    // ======================== 最小长度 ========================

    /**
     * 字符串最小长度限制。
     *
     * <p>仅当字段值非空时生效。值为 -1 表示不校验最小长度。
     * 长度以 {@code String.length()} 计算。
     *
     * @return 最小长度，默认 -1（不校验）
     */
    int minLength() default -1;

    // ======================== 最大长度 ========================

    /**
     * 字符串最大长度限制。
     *
     * <p>仅当字段值非空时生效。值为 -1 表示不校验最大长度。
     * 长度以 {@code String.length()} 计算。
     *
     * @return 最大长度，默认 -1（不校验）
     */
    int maxLength() default -1;

    // ======================== 正则表达式 ========================

    /**
     * 正则表达式校验规则。
     *
     * <p>仅当字段值非空时生效。值为空字符串表示不进行正则校验。
     * 使用 {@link java.util.regex.Pattern#matches} 进行匹配。
     *
     * @return 正则表达式字符串，默认为空
     */
    String regex() default "";

    // ======================== 错误消息 ========================

    /**
     * 校验失败时的错误消息。
     *
     * <p>当值为空字符串时，使用默认英文消息：
     * <ul>
     *   <li>必填："{fieldName} is required"</li>
     *   <li>正则："does not match pattern"</li>
     * </ul>
     *
     * @return 自定义错误消息，默认为空
     */
    String message() default "";
}

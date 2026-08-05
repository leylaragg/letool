package com.github.leyland.letool.sensitive.annotation;

import com.github.leyland.letool.sensitive.core.SensitiveType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要在 Jackson 序列化阶段脱敏的字符串字段。
 *
 * <p>Spring Boot 自动配置会注册字段级 Jackson 模块。该模块只接管带本注解的
 * {@link String} 字段，不会覆盖应用为普通字符串配置的序列化器。</p>
 *
 * <h3>配置优先级</h3>
 * <p>{@link #keepPrefix()} / {@link #keepSuffix()} / {@link #maskChar()} 的注解值覆盖策略默认值。
 * 值为 -1 时使用策略内置默认值（例如手机号默认 keepPrefix=3, keepSuffix=4）。</p>
 *
 * <h3>典型示例</h3>
 * <pre>{@code
 * public class User {
 *     // 内置类型：使用策略默认规则
 *     @Sensitive(type = SensitiveType.PHONE)
 *     private String phone;           // "13812345678" → "138****5678"
 *
 *     // 自定义正则：匹配 "工号" 后面的 4 位数字
 *     @Sensitive(type = SensitiveType.CUSTOM, pattern = "(?<=工号)\\d{4}", replacement = "****")
 *     private String employeeId;      // "工号123456" → "工号****56"
 *
 *     // 覆盖策略默认保留长度
 *     @Sensitive(type = SensitiveType.ID_CARD, keepPrefix = 6, keepSuffix = 4)
 *     private String idCard;          // "320123****1234"（保留前 6 位地区码）
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Sensitive {

    /**
     * 脱敏类型 —— 决定使用哪种内置策略（PHONE/ID_CARD/NAME/EMAIL/.../CUSTOM）。
     * 不能为 null。
     */
    SensitiveType type();

    /**
     * 自定义正则表达式 —— 仅 type = CUSTOM 时生效。
     * 匹配的内容将被 replacement 替换；CUSTOM 类型必须提供非空表达式。
     * 示例：{@code "(?<=工号)\\d{4}"} 匹配 "工号" 后面的 4 位数字。
     */
    String pattern() default "";

    /**
     * 替换字符串 —— 仅 type = CUSTOM 时生效，将 pattern 匹配的内容替换为此值。
     * 默认空字符串表示使用策略默认值；CUSTOM 类型默认使用单个星号。
     */
    String replacement() default "";

    /**
     * 保留前缀长度 —— 覆盖策略默认保留前缀长度。
     * 值为 -1 时使用策略内置默认值（如手机号默认 3，身份证默认 4）。
     * 示例：keepPrefix=3 手机号保留前 3 位 → "138****5678"。
     */
    int keepPrefix() default -1;

    /**
     * 保留后缀长度 —— 覆盖策略默认保留后缀长度。
     * 值为 -1 时使用策略内置默认值。
     * 示例：keepSuffix=4 身份证保留后 4 位 → "3201**********1234"。
     */
    int keepSuffix() default -1;

    /**
     * 遮盖字符 —— 覆盖策略默认的遮盖字符。
     * 默认 '*'（星号），可改为 '#'（井号）、'X' 等。
     * 示例：maskChar='#' 银行卡 → "6222####7890"。
     */
    char maskChar() default '*';
}

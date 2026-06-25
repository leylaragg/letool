package com.github.leyland.letool.sensitive.annotation;

import com.github.leyland.letool.sensitive.core.SensitiveType;

import java.lang.annotation.*;

/**
 * 字段级脱敏注解 —— 标记在需要脱敏的 String 字段上，Jackson 序列化 / 日志输出时自动生效.
 *
 * <h3>生效时机</h3>
 * <ul>
 *   <li><b>Jackson 序列化</b>：Controller 返回 JSON 时，{@link com.github.leyland.letool.sensitive.jackson.SensitiveJsonSerializer} 自动拦截</li>
 *   <li><b>编程式调用</b>：{@link com.github.leyland.letool.sensitive.core.SensitiveProcessor#mask(Object)} 反射扫描</li>
 *   <li><b>SensitiveUtil</b>：{@link com.github.leyland.letool.sensitive.util.SensitiveUtil#mask(String, SensitiveType)} 静态工具方法</li>
 * </ul>
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
     * 匹配的内容将被 replacement 替换。为空字符串表示不使用自定义正则。
     * 示例：{@code "(?<=工号)\\d{4}"} 匹配 "工号" 后面的 4 位数字。
     */
    String pattern() default "";

    /**
     * 替换字符串 —— 仅 type = CUSTOM 时生效，将 pattern 匹配的内容替换为此值。
     * 默认 "*" 替换为单个星号，可改为 "****" 替换为四个星号。
     */
    String replacement() default "*";

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

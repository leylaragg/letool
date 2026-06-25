package com.github.leyland.letool.tool.util;

import java.util.Collection;
import java.util.regex.Pattern;

/**
 * 通用校验工具——手机号、邮箱、URL、身份证、IP 地址等常用格式验证.
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>所有方法空安全：传入 {@code null} 返回 {@code false}，不抛 NPE</li>
 *   <li>正则常量预编译为 {@code static final}，避免重复编译</li>
 *   <li>校验规则偏向宽松（如 Email 只验证基本格式），严格校验请使用专业验证框架</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * if (!ValidatorUtil.isPhone(phone)) {
 *     throw new BusinessException("PARAM_001", "手机号格式不正确");
 * }
 * if (ValidatorUtil.isNullOrBlank(username)) {
 *     throw new BusinessException("PARAM_002", "用户名不能为空");
 * }
 * }</pre>
 */
public final class ValidatorUtil {

    /** 中国大陆手机号：1 开头，第二位 3-9，共 11 位 */
    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");
    /** 基本邮箱格式 */
    private static final Pattern EMAIL = Pattern.compile("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$");
    /** HTTP/HTTPS URL */
    private static final Pattern URL = Pattern.compile("^https?://[\\w.-]+(:\\d+)?(/.*)?$");
    /** 18 位身份证号（最后一位可为 X） */
    private static final Pattern ID_CARD_18 = Pattern.compile("^\\d{17}[\\dXx]$");
    /** IPv4 地址 */
    private static final Pattern IP_V4 = Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|[01]?\\d{1,2})\\.(25[0-5]|2[0-4]\\d|[01]?\\d{1,2})\\.(25[0-5]|2[0-4]\\d|[01]?\\d{1,2})\\.(25[0-5]|2[0-4]\\d|[01]?\\d{1,2})$");

    private ValidatorUtil() {}

    // ======================== 格式校验 ========================

    /**
     * 验证是否为中国大陆手机号（1 开头，第二位 3-9，11 位数字）.
     *
     * @param str 待验证字符串
     * @return {@code true} 如果格式正确
     */
    public static boolean isPhone(String str) {
        return str != null && PHONE.matcher(str).matches();
    }

    /**
     * 验证是否为基本合法邮箱格式（不含 IP 域名等极端情况）.
     *
     * @param str 待验证字符串
     * @return {@code true} 如果格式正确
     */
    public static boolean isEmail(String str) {
        return str != null && EMAIL.matcher(str).matches();
    }

    /**
     * 验证是否为 HTTP/HTTPS URL.
     *
     * @param str 待验证字符串
     * @return {@code true} 如果以 http:// 或 https:// 开头
     */
    public static boolean isUrl(String str) {
        return str != null && URL.matcher(str).matches();
    }

    /**
     * 验证是否为 18 位身份证号（仅校验格式，不验证校验位和地区）.
     *
     * @param str 待验证字符串
     * @return {@code true} 如果为 17 位数字 + 1 位数字或 X
     */
    public static boolean isIdCard(String str) {
        return str != null && ID_CARD_18.matcher(str).matches();
    }

    /**
     * 验证是否为合法 IPv4 地址.
     *
     * @param str 待验证字符串
     * @return {@code true} 如果为合法的 IPv4 地址
     */
    public static boolean isIpV4(String str) {
        return str != null && IP_V4.matcher(str).matches();
    }

    // ======================== 空值校验 ========================

    /**
     * 对象是否为 {@code null}.
     *
     * @param obj 任意对象
     * @return {@code true} 如果为 {@code null}
     */
    public static boolean isNull(Object obj) {
        return obj == null;
    }

    /**
     * 对象是否不为 {@code null}.
     *
     * @param obj 任意对象
     * @return {@code true} 如果不为 {@code null}
     */
    public static boolean isNotNull(Object obj) {
        return !isNull(obj);
    }

    /**
     * 布尔值是否为 {@link Boolean#TRUE}.
     *
     * @param b 布尔值
     * @return {@code true} 如果为 {@code Boolean.TRUE}
     */
    public static boolean isTrue(Boolean b) {
        return Boolean.TRUE.equals(b);
    }

    /**
     * 字符串是否为空、空白或 null（委托给 {@link StrUtil#isBlank(CharSequence)}）.
     *
     * @param cs 字符序列
     * @return {@code true} 如果为 null、空或纯空白字符
     */
    public static boolean isBlank(CharSequence cs) {
        return StrUtil.isBlank(cs);
    }

    /**
     * 字符串是否不为空且含非空白字符.
     *
     * @param cs 字符序列
     * @return {@code true} 如果非空且含非空白字符
     */
    public static boolean isNotBlank(CharSequence cs) {
        return StrUtil.isNotBlank(cs);
    }

    /**
     * 集合是否为 null 或无元素（委托给 {@link CollUtil#isEmpty(Collection)}）.
     *
     * @param coll 集合
     * @return {@code true} 如果为 null 或空
     */
    public static boolean isEmpty(Collection<?> coll) {
        return CollUtil.isEmpty(coll);
    }

    /**
     * 集合是否至少含有一个元素.
     *
     * @param coll 集合
     * @return {@code true} 如果非空且有元素
     */
    public static boolean isNotEmpty(Collection<?> coll) {
        return CollUtil.isNotEmpty(coll);
    }

    /**
     * 字符串是否完全匹配指定正则表达式.
     *
     * @param str   待匹配字符串
     * @param regex 正则表达式
     * @return {@code true} 如果完全匹配；{@code str} 或 {@code regex} 为 {@code null} 返回 {@code false}
     */
    public static boolean matches(String str, String regex) {
        return str != null && regex != null && Pattern.matches(regex, str);
    }
}

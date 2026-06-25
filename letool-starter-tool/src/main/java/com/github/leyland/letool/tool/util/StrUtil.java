package com.github.leyland.letool.tool.util;

import com.github.leyland.letool.tool.constant.SymbolConstant;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 字符串工具类——判空、格式化、驼峰/下划线互转、截取拼接等常用操作.
 *
 * <p>所有方法均为空安全：传入 {@code null} 不会抛出 NPE，而是返回安全的默认值.</p>
 */
public final class StrUtil {

    /** 驼峰 → 下划线转换正则：匹配小写字母/数字后接大写字母的位置 */
    private static final Pattern CAMEL_PATTERN = Pattern.compile("([a-z\\d])([A-Z])");
    /** 下划线 → 驼峰转换正则：匹配下划线后的单个字母 */
    private static final Pattern LINE_PATTERN = Pattern.compile("_(\\w)");

    private StrUtil() {}

    // ======================== 判空 ========================

    /**
     * 字符串是否为 {@code null} 或空串.
     *
     * @param cs 待检查的字符序列
     * @return {@code true} 如果为 {@code null} 或 {@code ""}
     */
    public static boolean isEmpty(CharSequence cs) {
        return cs == null || cs.length() == 0;
    }

    /**
     * 字符串是否非空（长度 &gt; 0）.
     */
    public static boolean isNotEmpty(CharSequence cs) {
        return !isEmpty(cs);
    }

    /**
     * 字符串是否为 {@code null}、空串或仅包含空白字符.
     *
     * @param cs 待检查的字符序列
     * @return {@code true} 如果无有效内容
     */
    public static boolean isBlank(CharSequence cs) {
        if (isEmpty(cs)) return true;
        for (int i = 0; i < cs.length(); i++) {
            if (!Character.isWhitespace(cs.charAt(i))) return false;
        }
        return true;
    }

    /**
     * 字符串是否包含至少一个非空白字符.
     */
    public static boolean isNotBlank(CharSequence cs) {
        return !isBlank(cs);
    }

    /**
     * 任意一个字符串为空.
     *
     * @param css 变长参数
     * @return 只要有一个为 {@code null} 或 {@code ""} 即返回 {@code true}
     */
    public static boolean hasEmpty(CharSequence... css) {
        for (CharSequence cs : css) {
            if (isEmpty(cs)) return true;
        }
        return false;
    }

    /**
     * 任意一个字符串为空白.
     *
     * @param css 变长参数
     * @return 只要有一个无有效内容即返回 {@code true}
     */
    public static boolean hasBlank(CharSequence... css) {
        for (CharSequence cs : css) {
            if (isBlank(cs)) return true;
        }
        return false;
    }

    // ======================== 默认值 ========================

    /**
     * 字符串为空时返回默认值.
     *
     * @param str        源字符串
     * @param defaultStr 默认值
     * @return {@code str} 非空则返回自身，否则返回 {@code defaultStr}
     */
    public static String defaultIfEmpty(String str, String defaultStr) {
        return isEmpty(str) ? defaultStr : str;
    }

    /**
     * 字符串为空白时返回默认值.
     *
     * @param str        源字符串
     * @param defaultStr 默认值
     * @return {@code str} 非空白则返回自身，否则返回 {@code defaultStr}
     */
    public static String defaultIfBlank(String str, String defaultStr) {
        return isBlank(str) ? defaultStr : str;
    }

    // ======================== 格式化 ========================

    /**
     * 使用 {@code {}} 占位符格式化字符串，类似 Slf4j 风格.
     *
     * <p>参数不足时保留剩余占位符原样输出；参数多余时忽略.</p>
     *
     * <pre>{@code
     * StrUtil.format("Hello, {}!", "World");  // → "Hello, World!"
     * StrUtil.format("{},{},{}", "a");        // → "a,{},{}"
     * }</pre>
     *
     * @param template 含 {@code {}} 占位符的模板
     * @param args     替换参数
     * @return 格式化后的字符串，模板为 {@code null} 时返回 {@code null}
     */
    public static String format(String template, Object... args) {
        if (template == null) return null;
        if (args == null || args.length == 0) return template;
        StringBuilder sb = new StringBuilder(template.length() + 64);
        int cursor = 0;
        int argIndex = 0;
        while (cursor < template.length()) {
            int brace = template.indexOf("{}", cursor);
            if (brace < 0) {
                sb.append(template.substring(cursor));
                break;
            }
            sb.append(template, cursor, brace);
            if (argIndex < args.length) {
                sb.append(args[argIndex++]);
            } else {
                sb.append("{}");
            }
            cursor = brace + 2;
        }
        return sb.toString();
    }

    // ======================== 驼峰/下划线转换 ========================

    /**
     * 下划线风格转驼峰风格.
     *
     * <pre>{@code
     * toCamelCase("user_name")  → "userName"
     * toCamelCase("HELLO_WORLD") → "helloWorld"
     * }</pre>
     *
     * @param str 下划线格式字符串
     * @return 小驼峰格式字符串，{@code null} 输入返回 {@code null}
     */
    public static String toCamelCase(String str) {
        if (str == null) return null;
        str = str.toLowerCase();
        Matcher m = LINE_PATTERN.matcher(str);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            sb.append(str, last, m.start());
            sb.append(m.group(1).toUpperCase());
            last = m.end();
        }
        sb.append(str.substring(last));
        return sb.toString();
    }

    /**
     * 驼峰风格转下划线风格.
     *
     * <pre>{@code
     * toSnakeCase("userName")  → "user_name"
     * toSnakeCase("HelloWorld") → "hello_world"
     * }</pre>
     *
     * @param str 驼峰格式字符串
     * @return 下划线格式（全小写）字符串，{@code null} 输入返回 {@code null}
     */
    public static String toSnakeCase(String str) {
        if (str == null) return null;
        return CAMEL_PATTERN.matcher(str).replaceAll("$1_$2").toLowerCase();
    }

    // ======================== 截取/省略 ========================

    /**
     * 超长截断并追加省略号.
     *
     * @param cs        字符序列
     * @param maxLength 最大长度（不含省略号）
     * @return 不超过 {@code maxLength + 3} 的字符串，超长部分以 "..." 代替
     */
    public static String truncate(CharSequence cs, int maxLength) {
        if (cs == null) return null;
        if (cs.length() <= maxLength) return cs.toString();
        return cs.subSequence(0, maxLength) + "...";
    }

    /**
     * 取左侧指定长度的子串.
     *
     * @param str 源字符串
     * @param len 长度，&le;0 返回空串，&ge;str.length() 返回原串
     * @return 左起 {@code len} 个字符
     */
    public static String left(String str, int len) {
        if (str == null) return null;
        if (len <= 0) return SymbolConstant.EMPTY;
        if (len >= str.length()) return str;
        return str.substring(0, len);
    }

    /**
     * 取右侧指定长度的子串.
     *
     * @param str 源字符串
     * @param len 长度，&le;0 返回空串，&ge;str.length() 返回原串
     * @return 末尾 {@code len} 个字符
     */
    public static String right(String str, int len) {
        if (str == null) return null;
        if (len <= 0) return SymbolConstant.EMPTY;
        if (len >= str.length()) return str;
        return str.substring(str.length() - len);
    }

    // ======================== 前缀/后缀 ========================

    /**
     * 移除字符串前缀（仅匹配一次）.
     *
     * @param str    源字符串
     * @param prefix 待移除的前缀
     * @return 若以 {@code prefix} 开头则返回剩余部分，否则返回原串
     */
    public static String removePrefix(String str, String prefix) {
        if (str == null || prefix == null) return str;
        if (str.startsWith(prefix)) return str.substring(prefix.length());
        return str;
    }

    /**
     * 移除字符串后缀（仅匹配一次）.
     *
     * @param str    源字符串
     * @param suffix 待移除的后缀
     * @return 若以 {@code suffix} 结尾则返回剩余部分，否则返回原串
     */
    public static String removeSuffix(String str, String suffix) {
        if (str == null || suffix == null) return str;
        if (str.endsWith(suffix)) return str.substring(0, str.length() - suffix.length());
        return str;
    }

    // ======================== 拼接 ========================

    /**
     * 将集合元素用分隔符拼接为字符串.
     *
     * @param coll      集合，{@code null} 返回空串
     * @param delimiter 分隔符
     * @return 拼接后的字符串
     */
    public static String join(Collection<?> coll, String delimiter) {
        if (coll == null || coll.isEmpty()) return SymbolConstant.EMPTY;
        return coll.stream().map(String::valueOf).collect(Collectors.joining(delimiter));
    }

    /**
     * 将数组元素用分隔符拼接为字符串.
     *
     * @param array     数组，{@code null} 返回空串
     * @param delimiter 分隔符
     * @return 拼接后的字符串
     */
    public static String join(Object[] array, String delimiter) {
        if (array == null || array.length == 0) return SymbolConstant.EMPTY;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < array.length; i++) {
            if (i > 0) sb.append(delimiter);
            sb.append(array[i]);
        }
        return sb.toString();
    }

    // ======================== 分割 ========================

    /**
     * 按分隔符切分字符串.
     *
     * <p>分隔符会被当作字面量（内部调用 {@code Pattern.quote}），不会作为正则解析.</p>
     *
     * @param str       源字符串
     * @param delimiter 分隔符
     * @return 切分后的数组，{@code str} 为 {@code null} 时返回空数组
     */
    public static String[] split(String str, String delimiter) {
        if (str == null) return new String[0];
        return str.split(Pattern.quote(delimiter));
    }

    // ======================== 常用操作 ========================

    /**
     * 安全比较两个字符序列是否内容相等.
     *
     * @param a 字符序列
     * @param b 字符序列
     * @return {@code true} 如果同为 {@code null} 或内容相等
     */
    public static boolean equals(CharSequence a, CharSequence b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.toString().equals(b.toString());
    }

    /**
     * 是否包含子序列.
     *
     * @param cs     字符序列
     * @param search 待查找的子序列
     * @return 任一为 {@code null} 返回 {@code false}
     */
    public static boolean contains(CharSequence cs, CharSequence search) {
        if (cs == null || search == null) return false;
        return cs.toString().contains(search);
    }

    /**
     * 首字母大写.
     *
     * @param str 源字符串
     * @return 首字母大写后的字符串，已大写或空串返回自身
     */
    public static String capitalize(String str) {
        if (isEmpty(str)) return str;
        char first = str.charAt(0);
        if (Character.isUpperCase(first)) return str;
        return Character.toUpperCase(first) + str.substring(1);
    }

    /**
     * 首字母小写.
     *
     * @param str 源字符串
     * @return 首字母小写后的字符串，已小写或空串返回自身
     */
    public static String uncapitalize(String str) {
        if (isEmpty(str)) return str;
        char first = str.charAt(0);
        if (Character.isLowerCase(first)) return str;
        return Character.toLowerCase(first) + str.substring(1);
    }
}

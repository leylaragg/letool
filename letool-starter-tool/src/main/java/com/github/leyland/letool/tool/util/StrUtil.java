package com.github.leyland.letool.tool.util;

import com.github.leyland.letool.tool.constant.SymbolConstant;
import com.github.leyland.letool.tool.value.ValueOperationException;

import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 提供判空、命名转换、Unicode 安全截取和拼接等常用字符串操作。
 *
 * <p>内容查询方法对 {@code null} 输入保持兼容；开发者配置类参数不合法时抛出
 * {@link ValueOperationException}，避免错误配置被静默处理。</p>
 */
public final class StrUtil {

    /** 截断超长文本时使用的默认省略标记。 */
    private static final String ELLIPSIS = "...";

    /**
     * 禁止创建工具类实例。
     */
    private StrUtil() {
    }

    /**
     * 判断字符序列是否为 {@code null} 或空串。
     *
     * @param cs 待检查字符序列
     * @return 为 {@code null} 或长度为零时返回 {@code true}
     */
    public static boolean isEmpty(CharSequence cs) {
        return cs == null || cs.length() == 0;
    }

    /**
     * 判断字符序列是否包含至少一个字符。
     *
     * @param cs 待检查字符序列
     * @return 不为空时返回 {@code true}
     */
    public static boolean isNotEmpty(CharSequence cs) {
        return !isEmpty(cs);
    }

    /**
     * 判断字符序列是否为 {@code null}、空串或仅包含 Unicode 空白字符。
     *
     * @param cs 待检查字符序列
     * @return 没有有效内容时返回 {@code true}
     */
    public static boolean isBlank(CharSequence cs) {
        if (isEmpty(cs)) {
            return true;
        }
        for (int offset = 0; offset < cs.length();) {
            int codePoint = Character.codePointAt(cs, offset);
            if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    /**
     * 判断字符序列是否包含至少一个非空白字符。
     *
     * @param cs 待检查字符序列
     * @return 包含有效内容时返回 {@code true}
     */
    public static boolean isNotBlank(CharSequence cs) {
        return !isBlank(cs);
    }

    /**
     * 判断参数中是否存在空字符序列。
     *
     * @param css 待检查字符序列；参数数组为 {@code null} 时视为缺失输入
     * @return 任一元素为空或参数数组缺失时返回 {@code true}
     */
    public static boolean hasEmpty(CharSequence... css) {
        if (css == null) {
            return true;
        }
        for (CharSequence cs : css) {
            if (isEmpty(cs)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断参数中是否存在空白字符序列。
     *
     * @param css 待检查字符序列；参数数组为 {@code null} 时视为缺失输入
     * @return 任一元素为空白或参数数组缺失时返回 {@code true}
     */
    public static boolean hasBlank(CharSequence... css) {
        if (css == null) {
            return true;
        }
        for (CharSequence cs : css) {
            if (isBlank(cs)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 字符串为空时返回默认值。
     *
     * @param str 源字符串
     * @param defaultStr 默认字符串
     * @return 源字符串非空时返回自身，否则返回默认字符串
     */
    public static String defaultIfEmpty(String str, String defaultStr) {
        return isEmpty(str) ? defaultStr : str;
    }

    /**
     * 字符串为空白时返回默认值。
     *
     * @param str 源字符串
     * @param defaultStr 默认字符串
     * @return 源字符串非空白时返回自身，否则返回默认字符串
     */
    public static String defaultIfBlank(String str, String defaultStr) {
        return isBlank(str) ? defaultStr : str;
    }

    /**
     * 使用 {@code {}} 占位符格式化字符串。
     *
     * <p>参数不足时保留剩余占位符，参数多余时忽略多余参数。</p>
     *
     * @param template 格式模板；为 {@code null} 时返回 {@code null}
     * @param args 替换参数
     * @return 格式化后的字符串
     */
    public static String format(String template, Object... args) {
        if (template == null) {
            return null;
        }
        if (args == null || args.length == 0) {
            return template;
        }
        StringBuilder result = new StringBuilder(template.length() + 32);
        int cursor = 0;
        int argumentIndex = 0;
        while (cursor < template.length()) {
            int placeholderIndex = template.indexOf("{}", cursor);
            if (placeholderIndex < 0) {
                result.append(template, cursor, template.length());
                break;
            }
            result.append(template, cursor, placeholderIndex);
            if (argumentIndex < args.length) {
                result.append(args[argumentIndex++]);
            } else {
                result.append("{}");
            }
            cursor = placeholderIndex + 2;
        }
        return result.toString();
    }

    /**
     * 将下划线命名转换为小驼峰命名。
     *
     * <p>连续下划线按一个分隔符处理，大小写转换固定使用根语言环境。</p>
     *
     * @param str 下划线格式字符串
     * @return 小驼峰格式字符串；输入为 {@code null} 时返回 {@code null}
     */
    public static String toCamelCase(String str) {
        if (str == null) {
            return null;
        }
        String normalized = str.toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(normalized.length());
        boolean capitalizeNext = false;
        for (int offset = 0; offset < normalized.length();) {
            int codePoint = normalized.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == '_') {
                capitalizeNext = result.length() > 0;
                continue;
            }
            result.appendCodePoint(capitalizeNext ? Character.toTitleCase(codePoint) : codePoint);
            capitalizeNext = false;
        }
        return result.toString();
    }

    /**
     * 将驼峰或缩写命名转换为下划线命名。
     *
     * <p>大写缩写与后续普通单词之间会保留边界，例如 {@code URLValue} 转换为
     * {@code url_value}。</p>
     *
     * @param str 驼峰或缩写格式字符串
     * @return 全小写下划线格式字符串；输入为 {@code null} 时返回 {@code null}
     */
    public static String toSnakeCase(String str) {
        if (str == null) {
            return null;
        }
        int[] codePoints = str.codePoints().toArray();
        StringBuilder result = new StringBuilder(str.length() + 8);
        for (int index = 0; index < codePoints.length; index++) {
            int current = codePoints[index];
            if (current == '_') {
                if (result.length() > 0 && result.charAt(result.length() - 1) != '_') {
                    result.append('_');
                }
                continue;
            }
            if (Character.isUpperCase(current) && shouldInsertBoundary(codePoints, index, result)) {
                result.append('_');
            }
            result.appendCodePoint(Character.toLowerCase(current));
        }
        int length = result.length();
        if (length > 0 && result.charAt(length - 1) == '_') {
            result.deleteCharAt(length - 1);
        }
        return result.toString();
    }

    /**
     * 判断当前大写字符前是否需要插入单词边界。
     *
     * @param codePoints 完整码点数组
     * @param index 当前码点下标
     * @param result 已生成结果
     * @return 需要插入下划线时返回 {@code true}
     */
    private static boolean shouldInsertBoundary(int[] codePoints, int index, StringBuilder result) {
        if (index == 0 || result.length() == 0 || result.charAt(result.length() - 1) == '_') {
            return false;
        }
        int previous = codePoints[index - 1];
        boolean previousIsWordTail = Character.isLowerCase(previous) || Character.isDigit(previous);
        boolean acronymEnds = Character.isUpperCase(previous)
                && index + 1 < codePoints.length
                && Character.isLowerCase(codePoints[index + 1]);
        return previousIsWordTail || acronymEnds;
    }

    /**
     * 按 Unicode 码点截断超长文本并追加省略标记。
     *
     * @param cs 源字符序列
     * @param maxLength 最大码点数量，不包含省略标记
     * @return 未超长时返回原内容，超长时返回截断内容和省略标记
     * @throws ValueOperationException 当最大长度小于零时抛出
     */
    public static String truncate(CharSequence cs, int maxLength) {
        requireNonNegative(maxLength, "maxLength");
        if (cs == null) {
            return null;
        }
        String value = cs.toString();
        int codePointCount = value.codePointCount(0, value.length());
        if (codePointCount <= maxLength) {
            return value;
        }
        int endOffset = value.offsetByCodePoints(0, maxLength);
        return value.substring(0, endOffset) + ELLIPSIS;
    }

    /**
     * 按 Unicode 码点获取左侧指定长度的子串。
     *
     * @param str 源字符串
     * @param len 码点数量；小于等于零时返回空串
     * @return 左侧子串；源字符串为 {@code null} 时返回 {@code null}
     */
    public static String left(String str, int len) {
        if (str == null) {
            return null;
        }
        if (len <= 0) {
            return SymbolConstant.EMPTY;
        }
        int codePointCount = str.codePointCount(0, str.length());
        if (len >= codePointCount) {
            return str;
        }
        return str.substring(0, str.offsetByCodePoints(0, len));
    }

    /**
     * 按 Unicode 码点获取右侧指定长度的子串。
     *
     * @param str 源字符串
     * @param len 码点数量；小于等于零时返回空串
     * @return 右侧子串；源字符串为 {@code null} 时返回 {@code null}
     */
    public static String right(String str, int len) {
        if (str == null) {
            return null;
        }
        if (len <= 0) {
            return SymbolConstant.EMPTY;
        }
        int codePointCount = str.codePointCount(0, str.length());
        if (len >= codePointCount) {
            return str;
        }
        return str.substring(str.offsetByCodePoints(0, codePointCount - len));
    }

    /**
     * 移除字符串的一次前缀匹配。
     *
     * @param str 源字符串
     * @param prefix 待移除前缀
     * @return 匹配时返回剩余部分，否则返回原字符串
     */
    public static String removePrefix(String str, String prefix) {
        if (str == null || prefix == null) {
            return str;
        }
        return str.startsWith(prefix) ? str.substring(prefix.length()) : str;
    }

    /**
     * 移除字符串的一次后缀匹配。
     *
     * @param str 源字符串
     * @param suffix 待移除后缀
     * @return 匹配时返回剩余部分，否则返回原字符串
     */
    public static String removeSuffix(String str, String suffix) {
        if (str == null || suffix == null) {
            return str;
        }
        return str.endsWith(suffix)
                ? str.substring(0, str.length() - suffix.length())
                : str;
    }

    /**
     * 使用指定分隔符拼接集合元素。
     *
     * @param coll 元素集合；为空时返回空串
     * @param delimiter 非 {@code null} 且非空的分隔符
     * @return 拼接后的字符串
     * @throws ValueOperationException 当分隔符为空时抛出
     */
    public static String join(Collection<?> coll, String delimiter) {
        requireDelimiter(delimiter);
        if (coll == null || coll.isEmpty()) {
            return SymbolConstant.EMPTY;
        }
        return coll.stream().map(String::valueOf).collect(Collectors.joining(delimiter));
    }

    /**
     * 使用指定分隔符拼接数组元素。
     *
     * @param array 元素数组；为空时返回空串
     * @param delimiter 非 {@code null} 且非空的分隔符
     * @return 拼接后的字符串
     * @throws ValueOperationException 当分隔符为空时抛出
     */
    public static String join(Object[] array, String delimiter) {
        requireDelimiter(delimiter);
        if (array == null || array.length == 0) {
            return SymbolConstant.EMPTY;
        }
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < array.length; index++) {
            if (index > 0) {
                result.append(delimiter);
            }
            result.append(array[index]);
        }
        return result.toString();
    }

    /**
     * 按字面量分隔符切分字符串。
     *
     * @param str 源字符串；为 {@code null} 时返回空数组
     * @param delimiter 非空字面量分隔符
     * @return 切分后的字符串数组
     * @throws ValueOperationException 当分隔符为空时抛出
     */
    public static String[] split(String str, String delimiter) {
        if (isEmpty(delimiter)) {
            throw ValueOperationException.invalidArgument("delimiter");
        }
        if (str == null) {
            return new String[0];
        }
        return str.split(Pattern.quote(delimiter));
    }

    /**
     * 安全比较两个字符序列内容是否相等。
     *
     * @param first 第一个字符序列
     * @param second 第二个字符序列
     * @return 同为 {@code null} 或内容相等时返回 {@code true}
     */
    public static boolean equals(CharSequence first, CharSequence second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        return first.toString().contentEquals(second);
    }

    /**
     * 判断字符序列是否包含目标子序列。
     *
     * @param cs 源字符序列
     * @param search 目标子序列
     * @return 任一参数为 {@code null} 时返回 {@code false}
     */
    public static boolean contains(CharSequence cs, CharSequence search) {
        return cs != null && search != null && cs.toString().contains(search);
    }

    /**
     * 按 Unicode 码点将首字符转换为大写。
     *
     * @param str 源字符串
     * @return 首字符大写后的字符串
     */
    public static String capitalize(String str) {
        return changeFirstCodePointCase(str, true);
    }

    /**
     * 按 Unicode 码点将首字符转换为小写。
     *
     * @param str 源字符串
     * @return 首字符小写后的字符串
     */
    public static String uncapitalize(String str) {
        return changeFirstCodePointCase(str, false);
    }

    /**
     * 转换字符串首码点大小写。
     *
     * @param str 源字符串
     * @param upperCase 是否转换为大写
     * @return 转换后的字符串
     */
    private static String changeFirstCodePointCase(String str, boolean upperCase) {
        if (isEmpty(str)) {
            return str;
        }
        int first = str.codePointAt(0);
        int converted = upperCase ? Character.toUpperCase(first) : Character.toLowerCase(first);
        if (first == converted) {
            return str;
        }
        int firstLength = Character.charCount(first);
        return new StringBuilder(str.length())
                .appendCodePoint(converted)
                .append(str, firstLength, str.length())
                .toString();
    }

    /**
     * 校验长度参数不能为负数。
     *
     * @param value 待校验长度
     * @param parameterName 安全的参数名称
     */
    private static void requireNonNegative(int value, String parameterName) {
        if (value < 0) {
            throw ValueOperationException.invalidArgument(parameterName);
        }
    }

    /**
     * 校验拼接分隔符不能为 {@code null} 或空串。
     *
     * @param delimiter 待校验分隔符
     */
    private static void requireDelimiter(String delimiter) {
        if (isEmpty(delimiter)) {
            throw ValueOperationException.invalidArgument("delimiter");
        }
    }
}

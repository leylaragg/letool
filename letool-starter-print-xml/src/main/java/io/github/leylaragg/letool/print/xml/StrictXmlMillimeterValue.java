package io.github.leylaragg.letool.print.xml;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 统一解析 XML 严格物理属性使用的毫米值。
 *
 * @author leyland
 */
final class StrictXmlMillimeterValue {

    /** 最多四位整数、三位小数的非负毫米格式。 */
    private static final Pattern UNSIGNED = Pattern.compile(
            "(?:0|[1-9][0-9]{0,3})(?:\\.[0-9]{1,3})?mm");

    /** 在非负格式基础上允许可选方向符号。 */
    private static final Pattern SIGNED = Pattern.compile(
            "[+-]?(?:0|[1-9][0-9]{0,3})(?:\\.[0-9]{1,3})?mm");

    /** 工具类不允许实例化。 */
    private StrictXmlMillimeterValue() {
    }

    /**
     * 判断属性是否符合非负毫米格式。
     *
     * @param value 待校验的属性值
     * @return 符合格式时返回 {@code true}
     */
    static boolean isUnsigned(String value) {
        return value != null && UNSIGNED.matcher(value.toLowerCase(Locale.ROOT)).matches();
    }

    /**
     * 判断属性是否符合可带方向的毫米格式。
     *
     * @param value 待校验的属性值
     * @return 符合格式时返回 {@code true}
     */
    static boolean isSigned(String value) {
        return value != null && SIGNED.matcher(value.toLowerCase(Locale.ROOT)).matches();
    }

    /**
     * 将已经通过严格格式校验的毫米值精确转换为微米。
     *
     * @param value 合法的毫米属性值
     * @return 整数微米值
     */
    static int toMicrometers(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return new BigDecimal(normalized.substring(0, normalized.length() - 2))
                .movePointRight(3)
                .intValueExact();
    }
}

package io.github.leylaragg.letool.print.xml;

import io.github.leylaragg.letool.print.document.style.DocumentColor;
import io.github.leylaragg.letool.print.document.style.DocumentLength;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * 把 XML 的受控标量统一转换为核心模型值。
 *
 * @author leyland
 */
final class XmlValueParser {

    /** 工具类不允许实例化。 */
    private XmlValueParser() {
    }

    /** 解析 mm、pt 或百分比长度。 */
    static DocumentLength length(
            String value, String templateCode, CompiledXmlNode node, String property) {
        if (value == null || value.isBlank()) {
            throw invalid(templateCode, node, property + " 不能为空");
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        try {
            if (normalized.endsWith("mm")) {
                return DocumentLength.millimeters(number(normalized, 2));
            }
            if (normalized.endsWith("pt")) {
                return DocumentLength.points(number(normalized, 2));
            }
            if (normalized.endsWith("%")) {
                return DocumentLength.percent(number(normalized, 1));
            }
        } catch (RuntimeException exception) {
            throw invalid(templateCode, node, property + " 不合法");
        }
        throw invalid(templateCode, node, property + " 的单位不受支持");
    }

    /** 解析精确到微米的非负毫米值。 */
    static int micrometers(
            String value, String templateCode, CompiledXmlNode node, String property) {
        DocumentLength length = length(value, templateCode, node, property);
        if (length.unit() != DocumentLength.Unit.MILLIMETER || length.value() < 0) {
            throw invalid(templateCode, node, property + " 必须使用非负 mm 单位");
        }
        try {
            return BigDecimal.valueOf(length.value()).movePointRight(3).intValueExact();
        } catch (ArithmeticException exception) {
            throw invalid(templateCode, node, property + " 超出支持范围");
        }
    }

    /** 解析 #RRGGBB 颜色。 */
    static DocumentColor color(
            String value, String templateCode, CompiledXmlNode node, String property) {
        if (value == null || !value.matches("#[0-9A-Fa-f]{6}")) {
            throw invalid(templateCode, node, property + " 必须使用 #RRGGBB");
        }
        return DocumentColor.rgb(
                Integer.parseInt(value.substring(1, 3), 16),
                Integer.parseInt(value.substring(3, 5), 16),
                Integer.parseInt(value.substring(5, 7), 16));
    }

    /** 严格解析 true 或 false。 */
    static boolean bool(
            String value, String templateCode, CompiledXmlNode node, String property) {
        if (!"true".equals(value) && !"false".equals(value)) {
            throw invalid(templateCode, node, property + " 必须为 true 或 false");
        }
        return Boolean.parseBoolean(value);
    }

    /** 把短横线标识转换为指定枚举。 */
    static <E extends Enum<E>> E enumValue(
            Class<E> type, String value, String templateCode,
            CompiledXmlNode node, String property) {
        try {
            return Enum.valueOf(type, value.replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw invalid(templateCode, node, property + " 不受支持");
        }
    }

    /** 解析正整数。 */
    static int positiveInteger(
            String value, String templateCode, CompiledXmlNode node, String property) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1) {
                throw new NumberFormatException("not positive");
            }
            return parsed;
        } catch (RuntimeException exception) {
            throw invalid(templateCode, node, property + " 必须为正整数");
        }
    }

    /** 删除长度单位后解析有限十进制值。 */
    private static double number(String value, int suffixLength) {
        double parsed = new BigDecimal(
                value.substring(0, value.length() - suffixLength)).doubleValue();
        if (!Double.isFinite(parsed)) {
            throw new NumberFormatException("not finite");
        }
        return parsed;
    }

    /** 生成带安全模板位置的编译异常。 */
    private static PrintCompilationException invalid(
            String templateCode, CompiledXmlNode node, String detail) {
        return XmlDiagnosticExceptions.path(templateCode, node, detail);
    }
}

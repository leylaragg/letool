package io.github.leylaragg.letool.print.xml.format;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 基于静态模式和区域的数字格式化器。
 *
 * @author leyland
 */
final class NumberValueFormatter implements PrintValueFormatter {

    /** 数字格式化器允许的选项。 */
    private static final Set<String> OPTIONS = Set.of("pattern", "locale", "rounding-mode");

    /** @return 内置数字格式化器名称 */
    @Override
    public String name() {
        return "number";
    }

    /**
     * 编译数字格式化计划。
     *
     * @param options 不可变静态选项
     * @param context 安全编译位置
     * @return 线程安全数字格式化计划
     */
    @Override
    public PrintFormatPlan compile(Map<String, String> options, FormatCompileContext context) {
        rejectUnknownOptions(options);
        String pattern = options.get("pattern");
        Locale locale = parseLocale(options.get("locale"));
        RoundingMode roundingMode = parseRoundingMode(options.get("rounding-mode"));
        if (pattern != null) {
            if (pattern.isBlank()) {
                throw new IllegalArgumentException("数字格式 pattern 不能为空白");
            }
            createDecimalFormat(pattern, locale, roundingMode);
        }
        return value -> format(value, pattern, locale, roundingMode);
    }

    /** 校验未知选项。 */
    private void rejectUnknownOptions(Map<String, String> options) {
        for (String option : options.keySet()) {
            if (!OPTIONS.contains(option)) {
                throw new IllegalArgumentException("number 包含未知格式选项：" + option);
            }
        }
    }

    /** 解析可选 BCP 47 区域标签。 */
    private Locale parseLocale(String value) {
        if (value == null) {
            return Locale.ROOT;
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException("数字格式 locale 不能为空白");
        }
        try {
            return new Locale.Builder().setLanguageTag(value).build();
        } catch (IllformedLocaleException exception) {
            throw new IllegalArgumentException("数字格式 locale 不是合法 BCP 47 标签", exception);
        }
    }

    /** 解析可选舍入模式。 */
    private RoundingMode parseRoundingMode(String value) {
        if (value == null) {
            return RoundingMode.HALF_EVEN;
        }
        try {
            return RoundingMode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("数字格式 rounding-mode 不受支持", exception);
        }
    }

    /** 将数字节点转换为稳定文本。 */
    private String format(JsonNode value, String pattern, Locale locale, RoundingMode roundingMode) {
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException("number 格式化器只接受数字节点");
        }
        BigDecimal number = BoundedDecimalText.normalize(value.decimalValue());
        if (pattern == null) {
            return number.toPlainString();
        }
        return createDecimalFormat(pattern, locale, roundingMode).format(number);
    }

    /** 每次调用创建局部 DecimalFormat，避免共享可变状态。 */
    private DecimalFormat createDecimalFormat(
            String pattern, Locale locale, RoundingMode roundingMode) {
        try {
            DecimalFormat formatter = new DecimalFormat(
                    pattern, DecimalFormatSymbols.getInstance(locale));
            formatter.setRoundingMode(roundingMode);
            formatter.setParseBigDecimal(true);
            return formatter;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("数字格式 pattern 不合法", exception);
        }
    }
}

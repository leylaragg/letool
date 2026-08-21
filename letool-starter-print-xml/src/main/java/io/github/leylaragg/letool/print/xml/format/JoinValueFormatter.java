package io.github.leylaragg.letool.print.xml.format;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.leylaragg.letool.print.xml.XmlDsl;

import java.util.Map;
import java.util.Set;

/**
 * 按数组顺序拼接 JSON 标量值的有界格式化器。
 *
 * @author leyland
 */
final class JoinValueFormatter implements PrintValueFormatter {

    /** join 只接受一个静态分隔符。 */
    private static final Set<String> OPTIONS = Set.of("separator");

    /** @return 内置数组拼接格式化器名称 */
    @Override
    public String name() {
        return "join";
    }

    /**
     * 冻结受控分隔符。
     *
     * @param options 不可变静态选项
     * @param context 安全编译位置
     * @return 可并发复用的数组拼接计划
     */
    @Override
    public PrintFormatPlan compile(Map<String, String> options, FormatCompileContext context) {
        for (String option : options.keySet()) {
            if (!OPTIONS.contains(option)) {
                throw new IllegalArgumentException("join 包含未知格式选项：" + option);
            }
        }
        String separator = options.getOrDefault("separator", ", ");
        if (separator.length() > 4_096
                || separator.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("join separator 不合法");
        }
        return value -> join(value, separator);
    }

    /**
     * 按原数组顺序拼接标量，并在每次写入前检查容量。
     *
     * @param value 待拼接的 JSON 数组
     * @param separator 静态分隔符
     * @return 拼接后的文本
     */
    private String join(JsonNode value, String separator) {
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException("join 格式化器只接受数组节点");
        }
        if (value.size() > XmlDsl.MAX_LOOP_ITEMS) {
            throw new IllegalArgumentException("join 数组元素数量超过限制");
        }
        StringBuilder result = new StringBuilder();
        boolean appended = false;
        for (JsonNode item : value) {
            if (item.isNull()) {
                continue;
            }
            String text = scalarText(item);
            if (appended) {
                appendBounded(result, separator);
            }
            appendBounded(result, text);
            appended = true;
        }
        return result.toString();
    }

    /**
     * 将 JSON 标量转成稳定文本，结构节点仍交给模板显式处理。
     *
     * @param value 数组中的非空元素
     * @return 标量文本
     */
    private String scalarText(JsonNode value) {
        if (value.isTextual()) {
            return value.textValue();
        }
        if (value.isNumber()) {
            return BoundedDecimalText.toPlainString(value.decimalValue());
        }
        if (value.isBoolean()) {
            return Boolean.toString(value.booleanValue());
        }
        throw new IllegalArgumentException("join 数组只能包含标量或空值");
    }

    /**
     * 在写入前检查最终文本上限。
     *
     * @param target 当前拼接结果
     * @param value 本次待追加文本
     */
    private void appendBounded(StringBuilder target, String value) {
        if ((long) target.length() + value.length()
                > XmlDsl.MAX_GENERATED_TEXT_CHARACTERS) {
            throw new IllegalArgumentException("join 生成文本字符数量超过限制");
        }
        target.append(value);
    }
}

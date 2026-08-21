package io.github.leylaragg.letool.print.xml.format;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Set;

/**
 * 把真实 JSON 布尔值映射为两个静态显示文本。
 *
 * @author leyland
 */
final class BooleanValueFormatter implements PrintValueFormatter {

    /** 布尔格式化器允许的静态选项。 */
    private static final Set<String> OPTIONS = Set.of("true-text", "false-text");

    /** @return 内置布尔格式化器名称 */
    @Override
    public String name() {
        return "boolean";
    }

    /**
     * 冻结 true 和 false 的显示文本。
     *
     * @param options 不可变静态选项
     * @param context 安全编译位置
     * @return 可并发复用的布尔格式计划
     */
    @Override
    public PrintFormatPlan compile(Map<String, String> options, FormatCompileContext context) {
        rejectUnknownOptions(options);
        String trueText = displayText(options.getOrDefault("true-text", "true"), "true-text");
        String falseText = displayText(options.getOrDefault("false-text", "false"), "false-text");
        return value -> format(value, trueText, falseText);
    }

    /** 未知选项通常表示模板拼写错误，应在发布时发现。 */
    private void rejectUnknownOptions(Map<String, String> options) {
        for (String option : options.keySet()) {
            if (!OPTIONS.contains(option)) {
                throw new IllegalArgumentException("boolean 包含未知格式选项：" + option);
            }
        }
    }

    /** 显示选项受长度和控制字符约束。 */
    private String displayText(String value, String name) {
        if (value == null || value.isEmpty() || value.length() > 4_096
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("boolean " + name + " 不合法");
        }
        return value;
    }

    /** 严格读取布尔节点，不接受字符串或数字隐式转换。 */
    private String format(JsonNode value, String trueText, String falseText) {
        if (value == null || !value.isBoolean()) {
            throw new IllegalArgumentException("boolean 格式化器只接受布尔节点");
        }
        return value.booleanValue() ? trueText : falseText;
    }
}

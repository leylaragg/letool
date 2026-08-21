package io.github.leylaragg.letool.print.xml;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

/**
 * 编译完成的结构化条件。
 *
 * @author leyland
 */
final class CompiledCondition {

    /** 条件操作符。 */
    enum Operator {
        EXISTS, NOT_EXISTS, IS_NULL, NOT_NULL, EMPTY, NOT_EMPTY,
        TRUTHY, FALSY, EQ, NE, GT, GTE, LT, LTE
    }

    /** 比较值类型。 */
    enum ValueType {
        STRING, NUMBER, BOOLEAN, NULL
    }

    /** 受限条件路径。 */
    private final CompiledDataPath path;

    /** 条件操作符。 */
    private final Operator operator;

    /** 比较值类型；无比较值时为 {@code null}。 */
    private final ValueType valueType;

    /** 已校验的比较值；空值比较和无比较值时为 {@code null}。 */
    private final Object expectedValue;

    /** 创建不可变条件。 */
    private CompiledCondition(
            CompiledDataPath path, Operator operator, ValueType valueType, Object expectedValue) {
        this.path = path;
        this.operator = operator;
        this.valueType = valueType;
        this.expectedValue = expectedValue;
    }

    /** 编译结构化条件属性。 */
    static CompiledCondition compile(
            Map<String, String> attributes,
            Set<String> variables,
            String templateCode,
            String tagPath,
            int line,
            int column) {
        CompiledDataPath path = CompiledDataPath.compile(
                attributes.get("path"), variables, templateCode, tagPath, line, column);
        Operator operator;
        try {
            operator = Operator.valueOf(attributes.getOrDefault("operator", "")
                    .replace('-', '_')
                    .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid(templateCode, tagPath, line, column, "条件操作符不受支持");
        }
        String value = attributes.get("value");
        String type = attributes.get("value-type");
        if (Set.of(Operator.EXISTS, Operator.NOT_EXISTS, Operator.IS_NULL,
                Operator.NOT_NULL, Operator.EMPTY, Operator.NOT_EMPTY,
                Operator.TRUTHY, Operator.FALSY).contains(operator)) {
            if (value != null || type != null) {
                throw invalid(templateCode, tagPath, line, column, "该条件操作符不允许比较值");
            }
            return new CompiledCondition(path, operator, null, null);
        }
        ValueType valueType;
        try {
            valueType = ValueType.valueOf(
                    type == null ? "STRING" : type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid(templateCode, tagPath, line, column, "条件比较值类型不受支持");
        }
        if (valueType == ValueType.NULL) {
            if (value != null) {
                throw invalid(templateCode, tagPath, line, column, "空值比较不能声明 value");
            }
            if (Set.of(Operator.GT, Operator.GTE, Operator.LT, Operator.LTE).contains(operator)) {
                throw invalid(templateCode, tagPath, line, column, "大小比较只支持数字");
            }
            return new CompiledCondition(path, operator, valueType, null);
        }
        if (value == null) {
            throw invalid(templateCode, tagPath, line, column, "条件比较必须声明 value");
        }
        Object expected = parseExpected(valueType, value, templateCode, tagPath, line, column);
        if (Set.of(Operator.GT, Operator.GTE, Operator.LT, Operator.LTE).contains(operator)
                && valueType != ValueType.NUMBER) {
            throw invalid(templateCode, tagPath, line, column, "大小比较只支持数字");
        }
        return new CompiledCondition(path, operator, valueType, expected);
    }

    /** 解析已经声明类型的比较值。 */
    private static Object parseExpected(
            ValueType type, String value, String templateCode, String tagPath, int line, int column) {
        try {
            return switch (type) {
                case STRING -> value;
                case NUMBER -> new BigDecimal(value);
                case BOOLEAN -> {
                    if (!"true".equals(value) && !"false".equals(value)) {
                        throw new IllegalArgumentException("invalid boolean");
                    }
                    yield Boolean.valueOf(value);
                }
                case NULL -> null;
            };
        } catch (RuntimeException exception) {
            throw invalid(templateCode, tagPath, line, column, "条件比较值与声明类型不一致");
        }
    }

    /** 计算结构化条件。 */
    boolean matches(BindingScope.ResolvedValue resolved, CompiledXmlNode node, String templateCode) {
        if (resolved.isInvalid()) {
            throw bindingError(node, templateCode,
                    "条件数据路径无法继续遍历：" + path.displayPath());
        }
        if (operator == Operator.EXISTS || operator == Operator.NOT_EXISTS) {
            return operator == Operator.EXISTS ? resolved.isPresent() : !resolved.isPresent();
        }
        if (!resolved.isPresent()) {
            throw bindingError(node, templateCode, "条件数据路径不存在：" + path.displayPath());
        }
        JsonNode actual = resolved.value();
        if (operator == Operator.IS_NULL || operator == Operator.NOT_NULL) {
            return operator == Operator.IS_NULL ? actual.isNull() : !actual.isNull();
        }
        if (operator == Operator.EMPTY || operator == Operator.NOT_EMPTY) {
            boolean empty;
            if (actual.isNull()) {
                empty = true;
            } else if (actual.isTextual()) {
                empty = actual.textValue().isEmpty();
            } else if (actual.isArray() || actual.isObject()) {
                empty = actual.size() == 0;
            } else {
                throw bindingError(node, templateCode,
                        "empty 条件只支持字符串、数组、对象或空值");
            }
            return operator == Operator.EMPTY ? empty : !empty;
        }
        if (operator == Operator.TRUTHY || operator == Operator.FALSY) {
            if (!actual.isBoolean() && !actual.isNull()) {
                throw bindingError(node, templateCode, "条件值类型必须为布尔或空值");
            }
            boolean truthy = actual.isBoolean() && actual.booleanValue();
            return operator == Operator.TRUTHY ? truthy : !truthy;
        }
        int comparison = compare(actual, node, templateCode);
        return switch (operator) {
            case EQ -> comparison == 0;
            case NE -> comparison != 0;
            case GT -> comparison > 0;
            case GTE -> comparison >= 0;
            case LT -> comparison < 0;
            case LTE -> comparison <= 0;
            default -> throw new IllegalStateException("未处理的条件操作符");
        };
    }

    /** 比较已经完成类型校验的实际值和期望值。 */
    private int compare(JsonNode actual, CompiledXmlNode node, String templateCode) {
        boolean matchesType = switch (valueType) {
            case STRING -> actual.isTextual();
            case NUMBER -> actual.isNumber();
            case BOOLEAN -> actual.isBoolean();
            case NULL -> actual.isNull();
        };
        if (!matchesType) {
            throw bindingError(node, templateCode, "条件值与比较值类型不一致");
        }
        return switch (valueType) {
            case STRING -> actual.textValue().compareTo((String) expectedValue);
            case NUMBER -> actual.decimalValue().compareTo((BigDecimal) expectedValue);
            case BOOLEAN -> Boolean.compare(actual.booleanValue(), (Boolean) expectedValue);
            case NULL -> 0;
        };
    }

    /** @return 条件数据路径 */
    CompiledDataPath path() {
        return path;
    }

    /** 创建安全编译异常。 */
    private static PrintCompilationException invalid(
            String templateCode, String tagPath, int line, int column, String detail) {
        return PrintCompilationException.invalid(templateCode + "：" + tagPath
                + "，第 " + line + " 行，第 " + column + " 列：" + detail);
    }

    /** 创建不包含业务值的安全绑定异常。 */
    private static io.github.leylaragg.letool.print.exception.PrintValidationException bindingError(
            CompiledXmlNode node, String templateCode, String detail) {
        return io.github.leylaragg.letool.print.exception.PrintValidationException.invalidDocument(
                templateCode + "：" + node.tagPath() + "，第 " + node.line()
                        + " 行，第 " + node.column() + " 列：" + detail);
    }
}

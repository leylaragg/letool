package io.github.leylaragg.letool.ruleengine.type;

import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;
import io.github.leylaragg.letool.ruleengine.internal.digest.DigestBuilder;

import java.util.List;

/**
 * 阶段一类型兼容和运算结果规则。
 *
 * <p>规则目录版本固定为一；任何兼容矩阵变更都必须显式升级目录规范并更新摘要。</p>
 */
public final class TypeCompatibility {

    /** 阶段一类型目录规范版本。 */
    public static final String CATALOG_VERSION = "1";

    /** 参与类型目录摘要的稳定规则清单。 */
    public static final List<String> CATALOG_RULES = List.of(
            "NUMERIC_PROMOTION:INTEGER_DECIMAL_TO_DECIMAL",
            "EQUALITY:EXACT_KIND_OR_NUMERIC",
            "ORDERING:NUMERIC_OR_EXACT_STRING_TEMPORAL",
            "TEMPORAL:NO_CROSS_KIND_CONVERSION",
            "NULL:EQUALITY_WITH_ANY_VALUE_OR_IS_NULL",
            "LOGICAL:BOOLEAN_ONLY",
            "IN:ELEMENT_EQUALITY_COMPATIBLE",
            "BETWEEN:SAME_ORDERING_DOMAIN",
            "DIVISION:DECIMAL128",
            "REMAINDER:BIG_DECIMAL_REMAINDER");

    /** 由目录版本和规则清单计算的 SHA-256 摘要。 */
    public static final String TYPE_CATALOG_DIGEST = calculateCatalogDigest();

    /** 工具类不允许实例化。 */
    private TypeCompatibility() {
    }

    /**
     * 判断实参类型能否赋给声明类型。
     *
     * @param actual 实际类型
     * @param expected 声明类型
     * @return 兼容时返回 {@code true}
     */
    public static boolean isAssignable(TypeDescriptor actual, TypeDescriptor expected) {
        require(actual, expected);
        if (actual.kind() == TypeKind.UNKNOWN) return true;
        if (actual.kind() == TypeKind.NULL) return expected.nullable();
        if (actual.nullable() && !expected.nullable()) return false;
        if (actual.kind() == expected.kind()) {
            if (actual.kind() != TypeKind.ARRAY) return true;
            return isAssignable(actual.elementType(), expected.elementType());
        }
        return actual.kind() == TypeKind.INTEGER && expected.kind() == TypeKind.DECIMAL;
    }

    /**
     * 推导数值二元运算结果。
     *
     * @param left 左类型
     * @param right 右类型
     * @return 数值结果；不兼容时为未知类型
     */
    public static TypeDescriptor numericResult(TypeDescriptor left, TypeDescriptor right) {
        require(left, right);
        if (isUnknown(left) || isUnknown(right)) return unknown();
        if (!isNumeric(left) || !isNumeric(right)) return unknown();
        TypeKind kind = left.kind() == TypeKind.DECIMAL || right.kind() == TypeKind.DECIMAL
                ? TypeKind.DECIMAL : TypeKind.INTEGER;
        return TypeDescriptor.scalar(kind, left.nullable() || right.nullable());
    }

    /**
     * 判断类型是否属于整数或小数。
     *
     * @param type 待判断类型
     * @return 类型是否属于整数或小数
     */
    public static boolean isNumeric(TypeDescriptor type) {
        if (type == null) throw RuleEngineException.invalidArgument();
        return type.kind() == TypeKind.INTEGER || type.kind() == TypeKind.DECIMAL;
    }

    /**
     * 判断两侧是否可做相等比较。
     *
     * @param left 左类型
     * @param right 右类型
     * @return 可比较时返回 {@code true}
     */
    public static boolean supportsEquality(TypeDescriptor left, TypeDescriptor right) {
        require(left, right);
        if (isUnknown(left) || isUnknown(right)) return true;
        if (left.kind() == TypeKind.NULL || right.kind() == TypeKind.NULL) return true;
        if (isNumeric(left) && isNumeric(right)) return true;
        return left.kind() == right.kind() && switch (left.kind()) {
            case STRING, BOOLEAN, DATE, DATE_TIME, INSTANT -> true;
            default -> false;
        };
    }

    /**
     * 判断两侧是否可做排序比较。
     *
     * @param left 左类型
     * @param right 右类型
     * @return 可比较时返回 {@code true}
     */
    public static boolean supportsOrdering(TypeDescriptor left, TypeDescriptor right) {
        require(left, right);
        if (isUnknown(left) || isUnknown(right)) return true;
        if (isNumeric(left) && isNumeric(right)) return true;
        return left.kind() == right.kind() && switch (left.kind()) {
            case STRING, DATE, DATE_TIME, INSTANT -> true;
            default -> false;
        };
    }

    /** @return 标准未知占位类型 */
    public static TypeDescriptor unknown() {
        return TypeDescriptor.scalar(TypeKind.UNKNOWN, true);
    }

    /** 判断类型推导是否因前置错误处于未知状态。 */
    private static boolean isUnknown(TypeDescriptor value) {
        return value.kind() == TypeKind.UNKNOWN;
    }

    /** 统一校验二元类型目录查询的非空输入。 */
    private static void require(TypeDescriptor left, TypeDescriptor right) {
        if (left == null || right == null) throw RuleEngineException.invalidArgument();
    }

    /** 按固定目录版本和规则顺序计算兼容矩阵摘要。 */
    private static String calculateCatalogDigest() {
        DigestBuilder digest = new DigestBuilder("LETOOL_TYPE_CATALOG_V1")
                .add(CATALOG_VERSION)
                .add(CATALOG_RULES.size());
        for (String rule : CATALOG_RULES) {
            digest.add(rule);
        }
        return digest.finish();
    }
}

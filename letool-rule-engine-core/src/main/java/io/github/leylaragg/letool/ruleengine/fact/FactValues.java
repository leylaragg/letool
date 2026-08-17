package io.github.leylaragg.letool.ruleengine.fact;

import io.github.leylaragg.letool.ruleengine.api.EngineLimits;
import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 公共事实值工厂与有界 Java 值规范化入口。
 */
public final class FactValues {
    /** 工具类不允许实例化。 */
    private FactValues() { }

    /**
     * 共享的空事实值单例。
     *
     * @return 空事实值单例
     */
    public static FactValue nullValue() { return NullFactValue.instance(); }

    /**
     * 创建字符串事实值。
     *
     * @param value 非空字符串
     * @return 字符串事实值
     * @throws RuleEngineException 值为空时抛出
     */
    public static FactValue string(String value) { return scalar(FactKind.STRING, value); }

    /**
     * 创建单字符字符串事实值。
     *
     * @param value 字符
     * @return 字符串事实值
     */
    public static FactValue string(char value) { return string(String.valueOf(value)); }

    /**
     * 创建布尔事实值。
     *
     * @param value 布尔值
     * @return 布尔事实值
     */
    public static FactValue booleanValue(boolean value) { return scalar(FactKind.BOOLEAN, value); }

    /**
     * 创建整数事实值。
     *
     * @param value 长整数
     * @return 整数事实值
     */
    public static FactValue integer(long value) { return integer(BigInteger.valueOf(value)); }

    /**
     * 创建大整数事实值。
     *
     * @param value 非空大整数
     * @return 整数事实值
     * @throws RuleEngineException 值为空时抛出
     */
    public static FactValue integer(BigInteger value) { return scalar(FactKind.INTEGER, value); }

    /**
     * 创建十进制事实值。
     *
     * @param value 非空十进制数
     * @return 十进制事实值
     * @throws RuleEngineException 值为空时抛出
     */
    public static FactValue decimal(BigDecimal value) { return scalar(FactKind.DECIMAL, value); }

    /**
     * 构造日期事实值。
     *
     * @param value 非空日期
     * @return 日期事实值
     * @throws RuleEngineException 值为空时抛出
     */
    public static FactValue date(LocalDate value) { return scalar(FactKind.DATE, value); }

    /**
     * 构造日期时间事实值。
     *
     * @param value 非空日期时间
     * @return 日期时间事实值
     * @throws RuleEngineException 值为空时抛出
     */
    public static FactValue dateTime(LocalDateTime value) { return scalar(FactKind.DATE_TIME, value); }

    /**
     * 创建时间点事实值。
     *
     * @param value 非空时间点
     * @return 时间点事实值
     * @throws RuleEngineException 值为空时抛出
     */
    public static FactValue instant(Instant value) { return scalar(FactKind.INSTANT, value); }

    /**
     * 使用默认预算规范化 Java 值。
     *
     * @param value Java 值；允许为空
     * @return 不可变事实值
     * @throws RuleEngineException 值类型不支持或超过预算时抛出
     */
    public static FactValue fromJavaValue(Object value) {
        return fromJavaValue(value, EngineLimits.defaults());
    }

    /**
     * 在指定预算内规范化 Java 值。
     * @param value Java 值
     * @param limits 资源限制
     * @return 不可变事实值
     * @throws RuleEngineException 限制为空、值类型不支持或超过预算时抛出
     */
    public static FactValue fromJavaValue(Object value, EngineLimits limits) {
        if (limits == null) throw RuleEngineException.invalidArgument();
        return new Normalizer(limits).normalize(value, 1);
    }

    /** 在同一规范化边界内构造规则事实根对象。 */
    static ObjectFactValue fromMap(Map<?, ?> value, EngineLimits limits) {
        if (value == null || limits == null) throw RuleEngineException.invalidArgument();
        return (ObjectFactValue) new Normalizer(limits).normalize(value, 1);
    }

    /** 创建已知不可变且类型匹配的标量值。 */
    private static FactValue scalar(FactKind kind, Object value) {
        if (value == null) throw RuleEngineException.invalidArgument();
        return new ScalarFactValue(kind, value);
    }

    /** 单次 Java 值规范化的预算、循环检测与可变状态。 */
    private static final class Normalizer {
        /** 本次转换统一遵守的事实预算。 */
        private final EngineLimits limits;

        /** 当前递归路径上的容器身份，用于拒绝循环引用。 */
        private final IdentityHashMap<Object, Boolean> active = new IdentityHashMap<>();

        /** 已展开并将进入最终事实树的节点数。 */
        private int nodes;

        /** 创建与其他转换隔离的规范化会话。 */
        private Normalizer(EngineLimits limits) { this.limits = limits; }

        /** 按最终树节点计数，并在当前递归路径上检测容器循环。 */
        private FactValue normalize(Object value, int depth) {
            // 每次分支引用都展开为独立不可变节点，因此节点预算精确约束最终事实树规模。
            if (depth > limits.getMaxFactDepth()) throw RuleEngineException.invalidArgument();
            countNode();
            if (isContainer(value)) {
                if (active.put(value, Boolean.TRUE) != null) throw RuleEngineException.invalidArgument();
            }
            FactValue result;
            try {
                result = normalizeValue(value, depth);
                return result;
            } finally {
                if (isContainer(value)) active.remove(value);
            }
        }

        /** 把白名单 Java 类型分派到对应事实值，不调用任意对象转换方法。 */
        private FactValue normalizeValue(Object value, int depth) {
            if (value == null) return nullValue();
            if (value instanceof String v) return string(v);
            if (value instanceof Character v) return string(v);
            if (value instanceof Boolean v) return booleanValue(v);
            if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)
                return integer(((Number) value).longValue());
            if (value instanceof BigInteger v) return integer(v);
            if (value instanceof BigDecimal v) return decimal(v);
            if (value instanceof Float v) {
                if (!Float.isFinite(v)) throw RuleEngineException.invalidArgument();
                return decimal(new BigDecimal(v.toString()));
            }
            if (value instanceof Double v) {
                if (!Double.isFinite(v)) throw RuleEngineException.invalidArgument();
                return decimal(new BigDecimal(v.toString()));
            }
            if (value instanceof LocalDate v) return date(v);
            if (value instanceof LocalDateTime v) return dateTime(v);
            if (value instanceof Instant v) return instant(v);
            if (value instanceof Map<?, ?> v) return normalizeMap(v, depth);
            if (value instanceof Collection<?> v) return normalizeCollection(v, depth);
            if (value.getClass().isArray()) return normalizeArray(value, depth);
            throw RuleEngineException.invalidArgument();
        }

        /** 保留映射迭代顺序，并要求所有键都是非空白字符串。 */
        private FactValue normalizeMap(Map<?, ?> source, int depth) {
            Map<String, FactValue> result = new LinkedHashMap<>();
            int count = 0;
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                if (++count > limits.getMaxContainerSize()) throw RuleEngineException.invalidArgument();
                if (!(entry.getKey() instanceof String key) || key.isBlank()) throw RuleEngineException.invalidArgument();
                result.put(key, normalize(entry.getValue(), depth + 1));
            }
            return new ObjectFactValue(result);
        }

        /** 在容器容量预算内按迭代顺序展开集合。 */
        private FactValue normalizeCollection(Collection<?> source, int depth) {
            List<FactValue> result = new ArrayList<>();
            int count = 0;
            for (Object item : source) {
                if (++count > limits.getMaxContainerSize()) throw RuleEngineException.invalidArgument();
                result.add(normalize(item, depth + 1));
            }
            return new ArrayFactValue(result);
        }

        /** 在容器容量预算内展开对象数组或基本类型数组。 */
        private FactValue normalizeArray(Object source, int depth) {
            int length = Array.getLength(source);
            if (length > limits.getMaxContainerSize()) throw RuleEngineException.invalidArgument();
            List<FactValue> result = new ArrayList<>();
            for (int index = 0; index < length; index++) result.add(normalize(Array.get(source, index), depth + 1));
            return new ArrayFactValue(result);
        }

        /** 消耗一个最终事实树节点预算。 */
        private void countNode() {
            if (++nodes > limits.getMaxFactNodes()) throw RuleEngineException.invalidArgument();
        }

        /** 判断值是否需要参与当前递归路径的身份循环检测。 */
        private static boolean isContainer(Object value) {
            return value instanceof Map<?, ?> || value instanceof Collection<?>
                    || value != null && value.getClass().isArray();
        }
    }
}

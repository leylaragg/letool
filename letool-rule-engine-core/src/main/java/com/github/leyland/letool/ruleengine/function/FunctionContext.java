package com.github.leyland.letool.ruleengine.function;

import com.github.leyland.letool.ruleengine.exception.RuleEngineException;
import com.github.leyland.letool.ruleengine.fact.RuleFacts;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 函数可读取的最小不可变调用上下文。
 */
public final class FunctionContext {

    /** 单次调用允许携带的元数据项数。 */
    private static final int MAX_METADATA_ENTRIES = 64;

    /** 元数据键的最大字符数。 */
    private static final int MAX_METADATA_KEY_LENGTH = 128;

    /** 元数据值的最大字符数。 */
    private static final int MAX_METADATA_VALUE_LENGTH = 4096;

    /** 本次求值的不可变事实快照。 */
    private final RuleFacts facts;

    /** 本次求值的区域设置。 */
    private final Locale locale;

    /** 本次求值的时区。 */
    private final ZoneId zoneId;

    /** 已有界复制的字符串调用元数据。 */
    private final Map<String, String> invocationMetadata;

    /** 接收单次函数调用的完整不可变上下文。 */
    private FunctionContext(
            RuleFacts facts,
            Locale locale,
            ZoneId zoneId,
            Map<String, String> invocationMetadata) {
        if (facts == null || locale == null || zoneId == null || invocationMetadata == null) {
            throw RuleEngineException.invalidArgument();
        }
        this.facts = facts;
        this.locale = locale;
        this.zoneId = zoneId;
        this.invocationMetadata = copyMetadata(invocationMetadata);
    }

    /**
     * 创建只读函数上下文。
     *
     * @param facts 不可变规则事实
     * @param locale 调用区域
     * @param zoneId 调用时区
     * <p>元数据最多六十四项，键最多一百二十八个字符，值最多四千零九十六个字符；
     * 外部映射通过有界迭代复制。</p>
     *
     * @param invocationMetadata 字符串调用元数据
     * @return 不可变上下文
     */
    public static FunctionContext of(
            RuleFacts facts,
            Locale locale,
            ZoneId zoneId,
            Map<String, String> invocationMetadata) {
        return new FunctionContext(facts, locale, zoneId, invocationMetadata);
    }

    /**
     * 函数可读取但不能修改的规则事实。
     *
     * @return 规则事实
     */
    public RuleFacts facts() {
        return facts;
    }

    /**
     * 文本或区域相关函数统一使用的调用区域。
     *
     * @return 区域
     */
    public Locale locale() {
        return locale;
    }

    /**
     * 时间转换函数统一使用的调用时区。
     *
     * @return 时区
     */
    public ZoneId zoneId() {
        return zoneId;
    }

    /**
     * 与外部映射隔离的只读调用元数据。
     *
     * @return 调用元数据
     */
    public Map<String, String> invocationMetadata() {
        return invocationMetadata;
    }

    /** 有界复制元数据，并拒绝空白键和过长文本。 */
    private static Map<String, String> copyMetadata(Map<String, String> source) {
        Map<String, String> copy = new LinkedHashMap<>();
        int visitedEntries = 0;
        try {
            for (Map.Entry<String, String> entry : source.entrySet()) {
                if (++visitedEntries > MAX_METADATA_ENTRIES) {
                    throw RuleEngineException.invalidArgument();
                }
                String key = entry.getKey();
                String value = entry.getValue();
                if (key == null || key.isBlank() || !key.equals(key.trim())
                        || key.length() > MAX_METADATA_KEY_LENGTH
                        || value == null || value.length() > MAX_METADATA_VALUE_LENGTH) {
                    throw RuleEngineException.invalidArgument();
                }
                copy.put(key, value);
            }
        } catch (RuntimeException exception) {
            throw RuleEngineException.invalidArgument();
        }
        return Map.copyOf(copy);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof FunctionContext that)) return false;
        return facts.equals(that.facts) && locale.equals(that.locale)
                && zoneId.equals(that.zoneId)
                && invocationMetadata.equals(that.invocationMetadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(facts, locale, zoneId, invocationMetadata);
    }

}

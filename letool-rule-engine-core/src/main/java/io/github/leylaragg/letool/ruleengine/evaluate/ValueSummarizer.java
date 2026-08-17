package io.github.leylaragg.letool.ruleengine.evaluate;

import io.github.leylaragg.letool.ruleengine.fact.FactValue;

/**
 * 将事实值转换为有界、可脱敏展示摘要的扩展点。
 */
public interface ValueSummarizer {

    /**
     * 生成不超过指定长度的安全摘要。
     *
     * @param value 不可变事实值
     * @param maximumLength 最大字符数
     * @return 安全摘要
     */
    String summarize(FactValue value, int maximumLength);
}

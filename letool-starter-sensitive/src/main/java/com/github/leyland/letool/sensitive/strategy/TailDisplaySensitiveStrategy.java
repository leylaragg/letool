package com.github.leyland.letool.sensitive.strategy;

import com.github.leyland.letool.sensitive.core.MaskContext;
import com.github.leyland.letool.sensitive.core.SensitiveStrategy;

/**
 * 仅展示尾部脱敏 —— 前缀全部遮盖，只保留末尾若干位（如支付尾号）.
 *
 * <pre>
 *   "6222021234567890" → "************7890"（默认保留后 4 位）
 *   "ORDER001"         → "***R001"           （通过 context 自定义 suffix=4）
 * </pre>
 *
 * <p>保留位数由 {@link MaskContext#getKeepSuffix()} 控制（默认 4 位）.
 * 可通过 {@link MaskContext#getMaskChar()} 覆盖默认遮盖字符.</p>
 */
public class TailDisplaySensitiveStrategy implements SensitiveStrategy<MaskContext> {

    /**
     * 按当前策略执行单值脱敏。
     *
     * @param value 原始字符串，可为 {@code null}
     * @param context 脱敏上下文，可为 {@code null} 以使用策略默认值
     * @return 脱敏结果；空值保持不变
     */
    @Override
    public String mask(String value, MaskContext context) {
        int suffix = MaskingSupport.keepSuffix(context, 4);
        return MaskingSupport.maskMiddle(value, 0, suffix, MaskingSupport.maskChar(context));
    }
}

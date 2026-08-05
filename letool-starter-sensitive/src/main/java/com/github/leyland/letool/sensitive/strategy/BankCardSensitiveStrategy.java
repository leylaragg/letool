package com.github.leyland.letool.sensitive.strategy;

import com.github.leyland.letool.sensitive.core.MaskContext;
import com.github.leyland.letool.sensitive.core.SensitiveStrategy;

/**
 * 银行卡脱敏 —— 保留前 4 位 + 后 4 位，中间用 {@code *} 填充.
 *
 * <pre>
 *   "6222021234567890" → "6222****7890"
 *   "622202"            → "******"（不足以安全保留首尾时完整遮盖）
 * </pre>
 *
 * <p>可通过 {@link MaskContext} 覆盖默认保留长度和遮盖字符.</p>
 */
public class BankCardSensitiveStrategy implements SensitiveStrategy<MaskContext> {

    /**
     * 按当前策略执行单值脱敏。
     *
     * @param value 原始字符串，可为 {@code null}
     * @param context 脱敏上下文，可为 {@code null} 以使用策略默认值
     * @return 脱敏结果；空值保持不变
     */
    @Override
    public String mask(String value, MaskContext context) {
        int prefix = MaskingSupport.keepPrefix(context, 4);
        int suffix = MaskingSupport.keepSuffix(context, 4);
        return MaskingSupport.maskMiddle(value, prefix, suffix, MaskingSupport.maskChar(context));
    }
}

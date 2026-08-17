package io.github.leylaragg.letool.sensitive.strategy;

import io.github.leylaragg.letool.sensitive.core.MaskContext;
import io.github.leylaragg.letool.sensitive.core.SensitiveStrategy;

/**
 * 军官证 / 港澳通行证脱敏 —— 保留首字（证件类型）+ 后 4 位，中间用 {@code *} 填充.
 *
 * <pre>
 *   "军1234567"  → "军****567"
 *   "H12345678"  → "H****5678"（港澳通行证）
 *   "军12"        → "***"
 * </pre>
 *
 * <p>适用于军官证、士兵证、港澳通行证、台湾通行证等非标准证件号码.
 * 可通过 {@link MaskContext} 覆盖默认保留长度和遮盖字符.</p>
 */
public class DomSensitiveStrategy implements SensitiveStrategy<MaskContext> {

    /**
     * 按当前策略执行单值脱敏。
     *
     * @param value 原始字符串，可为 {@code null}
     * @param context 脱敏上下文，可为 {@code null} 以使用策略默认值
     * @return 脱敏结果；空值保持不变
     */
    @Override
    public String mask(String value, MaskContext context) {
        int prefix = MaskingSupport.keepPrefix(context, 1);
        int suffix = MaskingSupport.keepSuffix(context, 4);
        return MaskingSupport.maskMiddle(value, prefix, suffix, MaskingSupport.maskChar(context));
    }
}

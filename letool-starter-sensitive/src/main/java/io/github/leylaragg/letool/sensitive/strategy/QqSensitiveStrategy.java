package io.github.leylaragg.letool.sensitive.strategy;

import io.github.leylaragg.letool.sensitive.core.MaskContext;
import io.github.leylaragg.letool.sensitive.core.SensitiveStrategy;

/**
 * QQ 号脱敏 —— 保留前 2 位 + 后 2 位，中间用 {@code *} 填充.
 *
 * <pre>
 *   "1234567890" → "12******90"
 *   "1234"        → "****"
 * </pre>
 *
 * <p>可通过 {@link MaskContext} 覆盖默认保留长度和遮盖字符.</p>
 */
public class QqSensitiveStrategy implements SensitiveStrategy<MaskContext> {

    /**
     * 按当前策略执行单值脱敏。
     *
     * @param value 原始字符串，可为 {@code null}
     * @param context 脱敏上下文，可为 {@code null} 以使用策略默认值
     * @return 脱敏结果；空值保持不变
     */
    @Override
    public String mask(String value, MaskContext context) {
        int prefix = MaskingSupport.keepPrefix(context, 2);
        int suffix = MaskingSupport.keepSuffix(context, 2);
        return MaskingSupport.maskMiddle(value, prefix, suffix, MaskingSupport.maskChar(context));
    }
}

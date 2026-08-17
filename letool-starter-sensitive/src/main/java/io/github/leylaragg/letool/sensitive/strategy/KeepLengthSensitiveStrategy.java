package io.github.leylaragg.letool.sensitive.strategy;

import io.github.leylaragg.letool.sensitive.core.MaskContext;
import io.github.leylaragg.letool.sensitive.core.SensitiveStrategy;

/**
 * 保留首尾长度脱敏 —— 保留前 N 位 + 后 M 位，中间用遮盖字符填充，适用于未分类的通用字段.
 *
 * <pre>
 *   // 默认保留首 1 位 + 尾 1 位
 *   "ABCDEF" → "A****F"
 *
 *   // 通过 MaskContext 自定义
 *   context = context.withKeepPrefix(2).withKeepSuffix(3);
 *   "ABCDEFGH" → "AB***FGH"
 * </pre>
 *
 * <p>这是一个通用策略，不预设特定业务语义.
 * 保留长度完全由 {@link MaskContext#getKeepPrefix()} / {@link MaskContext#getKeepSuffix()} 控制.</p>
 */
public class KeepLengthSensitiveStrategy implements SensitiveStrategy<MaskContext> {

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
        int suffix = MaskingSupport.keepSuffix(context, 1);
        return MaskingSupport.maskMiddle(value, prefix, suffix, MaskingSupport.maskChar(context));
    }
}

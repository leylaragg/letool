package io.github.leylaragg.letool.sensitive.strategy;

import io.github.leylaragg.letool.sensitive.core.MaskContext;
import io.github.leylaragg.letool.sensitive.core.SensitiveStrategy;

/**
 * 地址脱敏 —— 保留前段（省市区/街道），后段详细地址用 {@code *} 遮盖.
 *
 * <pre>
 *   "北京市海淀区中关村大街1号" → "北京市海淀区****"
 *   "上海市浦东新区陆家嘴环路1000号" → "上海市浦东新区****"
 *   "海淀" → "海*"
 * </pre>
 *
 * <p>保留长度默认为总长度的一半（{@code value.length() / 2}），
 * 可通过 {@link MaskContext#getKeepPrefix()} 自定义保留长度.
 * 可通过 {@link MaskContext#getMaskChar()} 覆盖默认遮盖字符.</p>
 */
public class AddressSensitiveStrategy implements SensitiveStrategy<MaskContext> {

    /**
     * 按当前策略执行单值脱敏。
     *
     * @param value 原始字符串，可为 {@code null}
     * @param context 脱敏上下文，可为 {@code null} 以使用策略默认值
     * @return 脱敏结果；空值保持不变
     */
    @Override
    public String mask(String value, MaskContext context) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int prefix = MaskingSupport.keepPrefix(context, value.length() / 2);
        return MaskingSupport.maskMiddle(value, prefix, 0, MaskingSupport.maskChar(context));
    }
}

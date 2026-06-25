package com.github.leyland.letool.sensitive.strategy;

import com.github.leyland.letool.sensitive.core.MaskContext;
import com.github.leyland.letool.sensitive.core.SensitiveStrategy;

/**
 * 地址脱敏 —— 保留前段（省市区/街道），后段详细地址用 {@code *} 遮盖.
 *
 * <pre>
 *   "北京市海淀区中关村大街1号" → "北京市海淀区****"
 *   "上海市浦东新区陆家嘴环路1000号" → "上海市浦东新区****"
 *   "广东省广州市" → "广东省广州市"（长度不足 3 位不处理）
 * </pre>
 *
 * <p>保留长度默认为总长度的一半（{@code value.length() / 2}），
 * 可通过 {@link MaskContext#getKeepPrefix()} 自定义保留长度.
 * 可通过 {@link MaskContext#getMaskChar()} 覆盖默认遮盖字符.</p>
 */
public class AddressSensitiveStrategy implements SensitiveStrategy<MaskContext> {

    @Override
    public String mask(String value, MaskContext context) {
        // 长度不足 3 位的不处理
        if (value == null || value.length() < 3) return value;

        // 保留前半段（默认一半长度，可通过 context 覆盖）
        int prefix = context != null && context.getKeepPrefix() > 0 ? context.getKeepPrefix() : value.length() / 2;
        char ch = context != null ? context.getMaskChar() : '*';

        if (value.length() <= prefix) return value;
        // 前半段保留 + 后半段全部遮盖
        return value.substring(0, prefix) + String.valueOf(ch).repeat(value.length() - prefix);
    }
}

package com.github.leyland.letool.pay.model;

import com.github.leyland.letool.pay.exception.PayException;
import com.github.leyland.letool.pay.exception.PayErrorCode;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 支付模型参数校验器。
 */
final class PayModelValidator {

    private static final String DEFAULT_CURRENCY = "CNY";

    private PayModelValidator() {
    }

    /**
     * 规范化可选的支付提供方名称。
     *
     * @param provider 支付提供方名称
     * @return 规范化后的名称，未指定时返回 {@code null}
     */
    static String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return null;
        }
        return provider.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 校验必填文本。
     *
     * @param value     待校验文本
     * @param fieldName 字段名称
     * @return 去除首尾空白后的文本
     */
    static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw PayException.of(PayErrorCode.REQUEST_INVALID, fieldName + "不能为空");
        }
        return value.trim();
    }

    /**
     * 校验可选文本并去除首尾空白。
     *
     * @param value 待校验文本
     * @return 规范化后的文本，空白值返回 {@code null}
     */
    static String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 校验必填对象。
     *
     * @param value 待校验对象
     * @param fieldName 字段名称
     * @param <T> 对象类型
     * @return 非空对象
     */
    static <T> T requireObject(T value, String fieldName) {
        if (value == null) {
            throw PayException.of(PayErrorCode.REQUEST_INVALID, fieldName + "不能为空");
        }
        return value;
    }

    /**
     * 校验人民币币种。
     *
     * @param currency 币种代码
     * @return 大写的人民币代码
     */
    static String requireCny(String currency) {
        String normalized = currency == null || currency.isBlank()
                ? DEFAULT_CURRENCY : currency.trim().toUpperCase(Locale.ROOT);
        if (!DEFAULT_CURRENCY.equals(normalized)) {
            throw PayException.of(PayErrorCode.REQUEST_INVALID, "当前仅支持人民币 CNY");
        }
        return normalized;
    }

    /**
     * 校验金额必须为正数且最多包含两位有效小数。
     *
     * @param amount    待校验金额
     * @param fieldName 字段名称
     * @return 原始金额
     */
    static BigDecimal requireAmount(BigDecimal amount, String fieldName) {
        if (amount == null || amount.signum() <= 0) {
            throw PayException.of(PayErrorCode.REQUEST_INVALID, fieldName + "必须大于 0");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw PayException.of(PayErrorCode.REQUEST_INVALID, fieldName + "最多支持两位小数");
        }
        return amount;
    }

    /**
     * 校验商户订单号和平台订单号至少存在一个。
     *
     * @param outTradeNo   商户订单号
     * @param transactionId 平台订单号
     */
    static void requirePaymentIdentifier(String outTradeNo, String transactionId) {
        if (normalizeText(outTradeNo) == null && normalizeText(transactionId) == null) {
            throw PayException.of(PayErrorCode.REQUEST_INVALID,
                    "商户订单号和平台订单号至少需要提供一个");
        }
    }

    /**
     * 创建不可变字符串映射快照。
     *
     * @param source 原始映射
     * @return 不可变映射快照
     */
    static Map<String, String> immutableCopy(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String normalizedKey = requireText(key, "扩展参数名称");
            if (value == null) {
                throw PayException.of(PayErrorCode.REQUEST_INVALID, "扩展参数值不能为空");
            }
            copy.put(normalizedKey, value);
        });
        return Collections.unmodifiableMap(copy);
    }
}

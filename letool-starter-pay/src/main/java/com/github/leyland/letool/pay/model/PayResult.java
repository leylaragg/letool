package com.github.leyland.letool.pay.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付结果模型，封装一次支付 / 查询 / 退款操作的结果。
 *
 * <p>该对象为不可变模型，所有字段通过构造函数传入，仅提供 getter 方法。
 * 可通过以下静态工厂方法便捷构造实例：</p>
 * <ul>
 *   <li>{@link #success(String, String, BigDecimal)} — 创建成功结果</li>
 *   <li>{@link #fail(String, String)} — 创建失败结果</li>
 *   <li>{@link #fromCallback(Map)} — 从回调参数中解析结果</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 */
public class PayResult {

    // ======================== 字段 ========================

    /** 是否支付成功 */
    private final boolean success;

    /** 商户订单号 */
    private final String outTradeNo;

    /** 第三方支付平台的交易流水号 */
    private final String transactionId;

    /** 支付状态 */
    private final PayStatus status;

    /** 订单总金额（元） */
    private final BigDecimal totalAmount;

    /** 第三方支付平台的订单号 / 渠道订单号 */
    private final String channelOrderNo;

    /** 错误码（失败时填充） */
    private final String errorCode;

    /** 错误描述信息（失败时填充） */
    private final String errorMessage;

    /** 原始响应数据，存放各渠道返回的原始参数，供调试或高级场景使用 */
    private final Map<String, Object> raw;

    // ======================== 构造方法 ========================

    /**
     * 构造支付结果（全参数）。
     *
     * @param success        是否成功
     * @param outTradeNo     商户订单号
     * @param transactionId  第三方交易流水号
     * @param status         支付状态
     * @param totalAmount    订单总金额
     * @param channelOrderNo 渠道订单号
     * @param errorCode      错误码
     * @param errorMessage   错误描述
     * @param raw            原始响应数据
     */
    public PayResult(boolean success, String outTradeNo, String transactionId, PayStatus status,
                     BigDecimal totalAmount, String channelOrderNo, String errorCode,
                     String errorMessage, Map<String, Object> raw) {
        this.success = success;
        this.outTradeNo = outTradeNo;
        this.transactionId = transactionId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.channelOrderNo = channelOrderNo;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.raw = raw != null ? Collections.unmodifiableMap(new HashMap<>(raw)) : Collections.emptyMap();
    }

    // ======================== 静态工厂方法 ========================

    /**
     * 创建支付成功的结果。
     *
     * @param outTradeNo    商户订单号
     * @param transactionId 第三方交易流水号
     * @param totalAmount   订单总金额
     * @return 成功的支付结果
     */
    public static PayResult success(String outTradeNo, String transactionId, BigDecimal totalAmount) {
        return new PayResult(true, outTradeNo, transactionId, PayStatus.SUCCESS,
                totalAmount, null, null, null, null);
    }

    /**
     * 创建支付失败的结果。
     *
     * @param errorCode    错误码
     * @param errorMessage 错误描述
     * @return 失败的支付结果
     */
    public static PayResult fail(String errorCode, String errorMessage) {
        return new PayResult(false, null, null, null,
                null, null, errorCode, errorMessage, null);
    }

    /**
     * 从支付平台回调参数中解析支付结果。
     *
     * <p>该方法会将回调参数原样存入 {@code raw} 字段中，由上层根据具体渠道自行解析。</p>
     *
     * @param callbackParams 回调请求中的参数
     * @return 包含原始数据的支付结果
     */
    public static PayResult fromCallback(Map<String, Object> callbackParams) {
        return new PayResult(true, null, null, PayStatus.SUCCESS,
                null, null, null, null, callbackParams);
    }

    // ======================== Getter ========================

    /**
     * 判断支付是否成功。
     *
     * @return {@code true} 成功，{@code false} 失败
     */
    public boolean isSuccess() { return success; }

    /**
     * 获取商户订单号。
     *
     * @return 商户订单号
     */
    public String getOutTradeNo() { return outTradeNo; }

    /**
     * 获取第三方交易流水号。
     *
     * @return 交易流水号
     */
    public String getTransactionId() { return transactionId; }

    /**
     * 获取支付状态。
     *
     * @return 支付状态
     */
    public PayStatus getStatus() { return status; }

    /**
     * 获取订单总金额。
     *
     * @return 订单总金额（元）
     */
    public BigDecimal getTotalAmount() { return totalAmount; }

    /**
     * 获取渠道订单号。
     *
     * @return 第三方平台订单号
     */
    public String getChannelOrderNo() { return channelOrderNo; }

    /**
     * 获取错误码。
     *
     * @return 错误码，成功时为 null
     */
    public String getErrorCode() { return errorCode; }

    /**
     * 获取错误描述。
     *
     * @return 错误描述，成功时为 null
     */
    public String getErrorMessage() { return errorMessage; }

    /**
     * 获取原始响应数据（只读）。
     *
     * @return 不可修改的原始数据 Map
     */
    public Map<String, Object> getRaw() { return raw; }
}

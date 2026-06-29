package com.github.leyland.letool.sms.exception;

// ======================== 类级别说明 ========================

/**
 * <p>短信异常 — 短信模块所有异常的根类型。</p>
 *
 * <h3>职责</h3>
 * <p>继承自 {@link RuntimeException}（非受检异常），用于在短信发送失败时向上抛出，
 * 调用方可根据需要决定是否捕获处理。</p>
 *
 * <h3>抛出场景</h3>
 * <ul>
 *   <li>短信服务商 API 调用失败。</li>
 *   <li>短信签名或模板未正确配置。</li>
 *   <li>手机号码格式校验不通过。</li>
 *   <li>短信发送频率超限。</li>
 *   <li>网络通信异常。</li>
 * </ul>
 *
 * <h3>典型用法</h3>
 * <pre>{@code
 * try {
 *     smsTemplate.builder().to("13800138000").template("SMS_001").param("code", "1234").send();
 * } catch (SmsException e) {
 *     log.error("短信发送失败: {}", e.getMessage());
 *     // 执行补偿逻辑
 * }
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class SmsException extends RuntimeException {

    // ======================== 构造方法 ========================

    /**
     * 使用消息文本构造短信异常。
     *
     * @param message 错误消息
     */
    public SmsException(String message) {
        super(message);
    }

    /**
     * 使用消息文本和原因异常构造短信异常。
     *
     * @param message 错误消息
     * @param cause   导致此异常的根本原因
     */
    public SmsException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.github.leyland.letool.sms.model;

// ======================== 类级别说明 ========================

/**
 * <p>短信发送结果模型 — 封装一次短信发送操作的结果。</p>
 *
 * <h3>职责</h3>
 * <p>{@code SmsResult} 作为短信发送操作的统一返回值，提供对发送结果的标准化访问接口。</p>
 *
 * <h3>字段说明</h3>
 * <ul>
 *   <li><b>success</b> — 发送是否成功。</li>
 *   <li><b>requestId</b> — 成功时返回的请求 ID（由短信服务商 API 分配）。</li>
 *   <li><b>errorCode</b> — 失败时的错误码。</li>
 *   <li><b>errorMessage</b> — 失败时的错误描述信息。</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <p>本类不提供公开构造方法，只能通过静态工厂方法创建：</p>
 * <ul>
 *   <li>{@link #success(String)} — 发送成功时使用。</li>
 *   <li>{@link #fail(String, String)} — 发送失败时使用。</li>
 * </ul>
 *
 * <h3>典型用法</h3>
 * <pre>{@code
 * SmsResult result = smsTemplate.builder()
 *     .to("13800138000")
 *     .template("SMS_001")
 *     .param("code", "1234")
 *     .send();
 *
 * if (result.isSuccess()) {
 *     log.info("短信发送成功, requestId={}", result.getRequestId());
 * } else {
 *     log.error("短信发送失败: [{}] {}", result.getErrorCode(), result.getErrorMessage());
 * }
 * }</pre>
 *
 * <h3>不可变性</h3>
 * <p>本类为不可变对象（immutable），所有字段均为 {@code final}，
 * 通过构造方法一次性赋值，无 Setter 方法，保证线程安全。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public class SmsResult {

    // ======================== 字段定义 ========================

    /** 发送是否成功 */
    private final boolean success;

    /** 请求 ID（成功时由短信服务商 API 分配） */
    private final String requestId;

    /** 失败时的错误码 */
    private final String errorCode;

    /** 失败时的错误描述信息 */
    private final String errorMessage;

    // ======================== 私有构造方法 ========================

    /**
     * 私有构造方法，只能通过静态工厂方法创建实例。
     *
     * @param success      是否成功
     * @param requestId    请求 ID（成功时有效）
     * @param errorCode    错误码（失败时有效）
     * @param errorMessage 错误描述（失败时有效）
     */
    private SmsResult(boolean success, String requestId, String errorCode, String errorMessage) {
        this.success = success;
        this.requestId = requestId;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    // ======================== 静态工厂方法 ========================

    /**
     * 创建发送成功的响应。
     *
     * @param requestId 短信服务商返回的请求 ID
     * @return 成功响应实例
     */
    public static SmsResult success(String requestId) {
        return new SmsResult(true, requestId, null, null);
    }

    /**
     * 创建发送失败的响应。
     *
     * @param errorCode    错误码
     * @param errorMessage 错误描述
     * @return 失败响应实例
     */
    public static SmsResult fail(String errorCode, String errorMessage) {
        return new SmsResult(false, null, errorCode, errorMessage);
    }

    // ======================== Getter ========================

    /**
     * 获取发送是否成功。
     *
     * @return {@code true} 表示发送成功
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 获取请求 ID。
     *
     * @return 请求 ID，仅在成功时有效；失败时为 {@code null}
     */
    public String getRequestId() {
        return requestId;
    }

    /**
     * 获取错误码。
     *
     * @return 错误码，仅在失败时有效；成功时为 {@code null}
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * 获取错误描述信息。
     *
     * @return 错误描述，仅在失败时有效；成功时为 {@code null}
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    // ======================== Object 方法重写 ========================

    /**
     * 返回短信发送结果的字符串表示，便于日志记录和调试。
     *
     * @return 格式化的结果字符串
     */
    @Override
    public String toString() {
        return "SmsResult{" +
                "success=" + success +
                ", requestId='" + requestId + '\'' +
                ", errorCode='" + errorCode + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}

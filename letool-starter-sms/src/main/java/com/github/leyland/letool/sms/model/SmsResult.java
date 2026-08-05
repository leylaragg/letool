package com.github.leyland.letool.sms.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 不可变短信发送结果。
 *
 * <p>结果同时保存厂商请求级信息和逐手机号状态，能够准确表达批量请求的部分失败。</p>
 */
public final class SmsResult {

    private final boolean success;
    private final String provider;
    private final String requestId;
    private final String code;
    private final String message;
    private final List<SmsRecipientResult> recipientResults;

    /**
     * 创建短信发送结果。
     *
     * @param success 整体是否成功
     * @param provider Provider 名称
     * @param requestId 厂商请求 ID
     * @param code 厂商结果码
     * @param message 厂商结果说明
     * @param recipientResults 逐手机号结果
     */
    private SmsResult(
            boolean success,
            String provider,
            String requestId,
            String code,
            String message,
            List<SmsRecipientResult> recipientResults) {
        this.success = success;
        this.provider = provider;
        this.requestId = requestId;
        this.code = code;
        this.message = message;
        this.recipientResults = immutableResults(recipientResults);
    }

    /**
     * 根据逐手机号状态创建发送结果。
     *
     * @param provider Provider 名称
     * @param requestId 厂商请求 ID
     * @param code 厂商请求级结果码
     * @param message 厂商请求级结果说明
     * @param recipientResults 逐手机号结果
     * @return 结构化发送结果
     */
    public static SmsResult fromRecipients(
            String provider,
            String requestId,
            String code,
            String message,
            List<SmsRecipientResult> recipientResults) {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("Provider 名称不能为空");
        }
        if (recipientResults == null || recipientResults.isEmpty()) {
            throw new IllegalArgumentException("逐手机号结果不能为空");
        }
        boolean success = recipientResults.stream().allMatch(SmsRecipientResult::isSuccess);
        return new SmsResult(success, provider, requestId, code, message, recipientResults);
    }

    /**
     * 创建兼容旧版单请求成功结果。
     *
     * @param requestId 请求 ID
     * @return 成功结果
     */
    public static SmsResult success(String requestId) {
        return new SmsResult(true, "unknown", requestId, null, null, Collections.emptyList());
    }

    /**
     * 创建兼容旧版单请求失败结果。
     *
     * @param errorCode 错误码
     * @param errorMessage 错误说明
     * @return 失败结果
     */
    public static SmsResult fail(String errorCode, String errorMessage) {
        return new SmsResult(false, "unknown", null, errorCode, errorMessage, Collections.emptyList());
    }

    /**
     * 判断全部手机号是否发送成功。
     *
     * @return 全部成功时返回 {@code true}
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 获取实际使用的 Provider。
     *
     * @return Provider 名称
     */
    public String getProvider() {
        return provider;
    }

    /**
     * 获取厂商请求 ID。
     *
     * @return 请求 ID；厂商未返回时可为 {@code null}
     */
    public String getRequestId() {
        return requestId;
    }

    /**
     * 获取厂商请求级结果码。
     *
     * @return 结果码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取厂商请求级结果说明。
     *
     * @return 结果说明
     */
    public String getMessage() {
        return message;
    }

    /**
     * 获取兼容旧版 API 的错误码。
     *
     * @return 失败时返回厂商结果码；成功时返回 {@code null}
     */
    public String getErrorCode() {
        return success ? null : code;
    }

    /**
     * 获取兼容旧版 API 的错误说明。
     *
     * @return 失败时返回厂商说明；成功时返回 {@code null}
     */
    public String getErrorMessage() {
        return success ? null : message;
    }

    /**
     * 获取逐手机号结果的不可变快照。
     *
     * @return 逐手机号结果
     */
    public List<SmsRecipientResult> getRecipientResults() {
        return recipientResults;
    }

    /**
     * 复制逐手机号结果。
     *
     * @param source 原始结果列表
     * @return 不可变结果列表
     */
    private static List<SmsRecipientResult> immutableResults(List<SmsRecipientResult> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        if (source.stream().anyMatch(item -> item == null)) {
            throw new IllegalArgumentException("逐手机号结果不能包含 null");
        }
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    /**
     * 返回不包含手机号和短信参数的安全诊断文本。
     *
     * @return 安全诊断文本
     */
    @Override
    public String toString() {
        return "SmsResult{" +
                "success=" + success +
                ", provider='" + provider + '\'' +
                ", requestId='" + requestId + '\'' +
                ", code='" + code + '\'' +
                ", recipientCount=" + recipientResults.size() +
                '}';
    }
}

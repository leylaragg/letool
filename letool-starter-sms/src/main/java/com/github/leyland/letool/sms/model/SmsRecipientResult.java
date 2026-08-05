package com.github.leyland.letool.sms.model;

/**
 * 单个手机号的短信发送结果。
 */
public final class SmsRecipientResult {

    private final String phone;
    private final boolean success;
    private final String code;
    private final String message;

    /**
     * 创建单个手机号结果。
     *
     * @param phone 目标手机号
     * @param success 是否成功
     * @param code 厂商结果码
     * @param message 厂商结果说明
     */
    private SmsRecipientResult(String phone, boolean success, String code, String message) {
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("结果手机号不能为空");
        }
        this.phone = phone;
        this.success = success;
        this.code = code;
        this.message = message;
    }

    /**
     * 创建发送成功结果。
     *
     * @param phone 目标手机号
     * @param code 厂商成功码
     * @param message 厂商成功说明
     * @return 成功结果
     */
    public static SmsRecipientResult success(String phone, String code, String message) {
        return new SmsRecipientResult(phone, true, code, message);
    }

    /**
     * 创建发送失败结果。
     *
     * @param phone 目标手机号
     * @param code 厂商错误码
     * @param message 厂商错误说明
     * @return 失败结果
     */
    public static SmsRecipientResult failure(String phone, String code, String message) {
        return new SmsRecipientResult(phone, false, code, message);
    }

    /**
     * 获取目标手机号。
     *
     * @return 目标手机号
     */
    public String getPhone() {
        return phone;
    }

    /**
     * 判断该手机号是否发送成功。
     *
     * @return 成功时返回 {@code true}
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 获取厂商结果码。
     *
     * @return 结果码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取厂商结果说明。
     *
     * @return 结果说明
     */
    public String getMessage() {
        return message;
    }
}

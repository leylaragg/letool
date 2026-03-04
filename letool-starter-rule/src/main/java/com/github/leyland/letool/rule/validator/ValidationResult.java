package com.github.leyland.letool.rule.validator;

import lombok.Data;

/**
 * 验证结果对象
 * 封装验证结果信息
 *
 * @author leyland
 * @since 2026/02/14
 */
@Data
public class ValidationResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 结果消息
     */
    private String message;

    /**
     * 错误代码
     */
    private String errorCode;

    private ValidationResult() {
    }

    /**
     * 创建成功结果
     */
    public static ValidationResult success() {
        ValidationResult result = new ValidationResult();
        result.setSuccess(true);
        result.setMessage("验证通过");
        return result;
    }

    /**
     * 创建成功结果（带消息）
     */
    public static ValidationResult success(String message) {
        ValidationResult result = new ValidationResult();
        result.setSuccess(true);
        result.setMessage(message);
        return result;
    }

    /**
     * 创建失败结果
     */
    public static ValidationResult error(String message) {
        ValidationResult result = new ValidationResult();
        result.setSuccess(false);
        result.setMessage(message);
        return result;
    }

    /**
     * 创建失败结果（带错误码）
     */
    public static ValidationResult error(String errorCode, String message) {
        ValidationResult result = new ValidationResult();
        result.setSuccess(false);
        result.setErrorCode(errorCode);
        result.setMessage(message);
        return result;
    }
}

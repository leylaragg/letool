package com.github.leyland.letool.excel.exception;

import com.github.leyland.letool.exception.code.ErrorCode;

/**
 * Excel 模块对外暴露的稳定错误码。
 */
public enum ExcelErrorCode implements ErrorCode {

    /** 工作簿读取或解析失败。 */
    READ_FAILED("EXCEL_001", "Excel 读取失败"),

    /** 工作簿生成或写出失败。 */
    WRITE_FAILED("EXCEL_002", "Excel 写入失败"),

    /** 校验规则执行过程中发生技术故障。 */
    VALIDATION_FAILED("EXCEL_003", "Excel 数据校验失败");

    /** 供程序判断和响应映射使用的稳定错误码。 */
    private final String code;

    /** 未配置国际化文案时使用的默认消息。 */
    private final String defaultMessage;

    /**
     * 创建 Excel 错误码定义。
     *
     * @param code 稳定的机器可读错误码
     * @param defaultMessage 默认的人类可读消息
     */
    ExcelErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 获取稳定错误码。
     *
     * @return 非空错误码
     */
    @Override
    public String getCode() {
        return code;
    }

    /**
     * 获取默认错误消息。
     *
     * @return 非空默认消息
     */
    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }
}

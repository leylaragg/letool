package com.github.leyland.data.letool.tool.api;

/**
 * @ClassName <h2>SyStemCode</h2>
 * @Description TODO 系统状态码
 * @Author Rungo
 * @Version 1.0
 **/
public enum SystemCode implements IResultCode {

    SYSTEM_ERROR(5001, "系统错误"),

    INJECT_ERROR(5017, "注入失败"),

    ;



    /**
     * code编码
     */
    final int code;
    /**
     * 中文信息描述
     */
    final String message;

    SystemCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 获取消息
     *
     * @return message
     */
    @Override
    public String getMessage() {
        return this.message;
    }

    /**
     * 获取状态码
     *
     * @return code
     */
    @Override
    public int getCode() {
        return this.code;
    }
}

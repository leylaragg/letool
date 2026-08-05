package com.github.leyland.letool.pay.model;

/**
 * 支付下单后需要由调用方执行的动作类型。
 *
 * @author leyland
 * @since 2.0.0
 */
public enum PayActionType {

    /** 无需额外动作。 */
    NONE,

    /** 将表单 HTML 输出给浏览器。 */
    FORM_HTML,

    /** 将用户重定向到指定地址。 */
    REDIRECT_URL,

    /** 展示二维码链接。 */
    QR_CODE_URL,

    /** 将应用支付字符串交给移动端 SDK。 */
    APP_ORDER_STRING,

    /** 将 JSAPI 参数交给前端调用。 */
    JSAPI_PARAMETERS
}

package io.github.leylaragg.letool.pay.model;

/**
 * 支付场景。
 *
 * @author leyland
 * @since 2.0.0
 */
public enum PayScene {

    /** 电脑网站支付。 */
    PAGE,

    /** 手机网站支付。 */
    WAP,

    /** 移动应用支付。 */
    APP,

    /** 扫码支付。 */
    QR_CODE,

    /** 微信公众号或小程序支付。 */
    JSAPI
}

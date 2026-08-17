package io.github.leylaragg.letool.pay.model;

/**
 * 退款状态。
 *
 * @author leyland
 * @since 2.0.0
 */
public enum RefundStatus {

    /** 退款处理中。 */
    PROCESSING,

    /** 退款成功。 */
    SUCCESS,

    /** 退款已关闭。 */
    CLOSED,

    /** 退款失败。 */
    FAILED,

    /** 平台结果暂时无法确定。 */
    UNKNOWN
}

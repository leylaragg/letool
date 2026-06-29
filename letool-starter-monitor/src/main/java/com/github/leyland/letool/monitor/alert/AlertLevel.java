package com.github.leyland.letool.monitor.alert;

/**
 * 告警级别枚举.
 *
 * <p>定义告警的严重程度，影响通知策略和处理优先级。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public enum AlertLevel {

    /**
     * 警告级别 —— 性能抖动、资源使用率偏高，需关注但暂时不影响服务.
     */
    WARN,

    /**
     * 严重级别 —— 服务不可用、资源耗尽等紧急情况，需要立即介入处理.
     */
    CRITICAL
}

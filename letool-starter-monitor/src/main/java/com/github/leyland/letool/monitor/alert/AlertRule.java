package com.github.leyland.letool.monitor.alert;

import java.util.ArrayList;
import java.util.List;

/**
 * 告警规则模型.
 *
 * <p>定义一条监控告警规则，包括触发条件、阈值、持续时长、告警级别和通知渠道。
 * 当指定指标满足条件并持续一定时间后，将通过配置的通知渠道发送告警。</p>
 *
 * <h3>示例</h3>
 * <pre>{@code
 * AlertRule rule = new AlertRule();
 * rule.setName("堆内存使用率过高");
 * rule.setMetric("heap.used.percent");
 * rule.setCondition(AlertCondition.GREATER_THAN);
 * rule.setThreshold(0.85);
 * rule.setDuration("5m");
 * rule.setLevel(AlertLevel.CRITICAL);
 * rule.setMessage("堆内存使用率已达 {value}%，请及时排查！");
 * rule.setNotifierTypes(List.of("dingtalk", "mail"));
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class AlertRule {

    // ======================== 字段 ========================

    /** 告警规则名称（唯一标识） */
    private String name;

    /** 监控指标名称，例如 {@code "heap.used.percent"} */
    private String metric;

    /** 比较条件 */
    private AlertCondition condition = AlertCondition.GREATER_THAN;

    /** 告警阈值 */
    private double threshold;

    /** 持续时长，例如 {@code "5m"}、{@code "30s"}，指标需持续超阈值该时长才触发告警 */
    private String duration;

    /** 告警级别 */
    private AlertLevel level = AlertLevel.WARN;

    /** 告警消息模板，支持 {@code {metric}}、{@code {value}}、{@code {threshold}} 占位符 */
    private String message;

    /** 通知渠道类型列表：dingtalk、wechat、mail */
    private List<String> notifierTypes = new ArrayList<>();

    // ======================== 构造方法 ========================

    /** 创建空的告警规则. */
    public AlertRule() {
    }

    /**
     * 创建完整的告警规则.
     *
     * @param name          规则名称
     * @param metric        监控指标名称
     * @param condition     比较条件
     * @param threshold     告警阈值
     * @param duration      持续时长
     * @param level         告警级别
     * @param message       消息模板
     * @param notifierTypes 通知渠道类型列表
     */
    public AlertRule(String name, String metric, AlertCondition condition, double threshold,
                     String duration, AlertLevel level, String message, List<String> notifierTypes) {
        this.name = name;
        this.metric = metric;
        this.condition = condition;
        this.threshold = threshold;
        this.duration = duration;
        this.level = level;
        this.message = message;
        this.notifierTypes = notifierTypes;
    }

    // ======================== Getter / Setter ========================

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }

    public AlertCondition getCondition() { return condition; }
    public void setCondition(AlertCondition condition) { this.condition = condition; }

    public double getThreshold() { return threshold; }
    public void setThreshold(double threshold) { this.threshold = threshold; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }

    public AlertLevel getLevel() { return level; }
    public void setLevel(AlertLevel level) { this.level = level; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<String> getNotifierTypes() { return notifierTypes; }
    public void setNotifierTypes(List<String> notifierTypes) { this.notifierTypes = notifierTypes; }

    // ======================== 便捷方法 ========================

    /**
     * 格式化告警消息，将模板中的占位符替换为实际值.
     *
     * @param value 当前指标值
     * @return 格式化后的告警消息
     */
    public String formatMessage(double value) {
        if (message == null) {
            return String.format("[%s] %s %s %s (当前值: %.2f)",
                    level, metric, condition.getSymbol(), threshold, value);
        }
        return message.replace("{metric}", metric)
                .replace("{value}", String.format("%.2f", value))
                .replace("{threshold}", String.format("%.2f", threshold));
    }

    /**
     * 判断当前值是否触发告警条件.
     *
     * @param value 当前指标值
     * @return {@code true} 如果触发告警条件
     */
    public boolean isTriggered(double value) {
        switch (condition) {
            case GREATER_THAN:
                return value > threshold;
            case LESS_THAN:
                return value < threshold;
            case EQUAL:
                return Math.abs(value - threshold) < 0.0001;
            default:
                return false;
        }
    }

    @Override
    public String toString() {
        return "AlertRule{name='" + name + "', metric='" + metric + "', "
                + condition + " " + threshold + ", duration=" + duration
                + ", level=" + level + "}";
    }
}

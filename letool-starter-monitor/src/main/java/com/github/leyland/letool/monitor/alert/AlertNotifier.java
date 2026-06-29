package com.github.leyland.letool.monitor.alert;

import com.github.leyland.letool.monitor.config.MonitorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 告警通知分发器.
 *
 * <p>管理多个告警通知渠道（钉钉、企业微信、邮件等），根据告警规则中配置的
 * {@code notifierTypes} 将告警消息分发给匹配的渠道。</p>
 *
 * <p>通知渠道通过 {@link AlertChannel} 接口统一抽象，可扩展自定义渠道。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * AlertNotifier notifier = new AlertNotifier(properties);
 * notifier.registerChannel(new DingTalkNotifier(properties));
 * notifier.registerChannel(new WechatNotifier(properties));
 * notifier.notify(rule, "CPU 使用率超过 90%");
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class AlertNotifier {

    // ======================== 日志 ========================

    private static final Logger log = LoggerFactory.getLogger(AlertNotifier.class);

    // ======================== 字段 ========================

    /** 配置属性 */
    private final MonitorProperties properties;

    /** 已注册的通知渠道列表（线程安全） */
    private final List<AlertChannel> channels = new CopyOnWriteArrayList<>();

    // ======================== 构造方法 ========================

    /**
     * 创建告警通知分发器.
     *
     * @param properties 监控模块配置属性
     */
    public AlertNotifier(MonitorProperties properties) {
        this.properties = properties;
    }

    // ======================== 公共方法 ========================

    /**
     * 发送告警通知.
     *
     * <p>根据规则中配置的 {@code notifierTypes} 匹配已注册的渠道并逐一发送。
     * 如果规则未配置通知类型，则向所有已注册渠道发送。</p>
     *
     * @param rule    告警规则
     * @param message 告警消息内容
     */
    public void notify(AlertRule rule, String message) {
        if (!properties.getAlert().isEnabled()) {
            log.debug("[Monitor-Alert] 告警通知未启用，跳过发送");
            return;
        }

        List<String> types = rule.getNotifierTypes();
        if (types == null || types.isEmpty()) {
            log.warn("[Monitor-Alert] 告警规则 \"{}\" 未配置通知渠道，将向所有已注册渠道发送", rule.getName());
            for (AlertChannel channel : channels) {
                sendToChannel(channel, rule, message);
            }
            return;
        }

        for (AlertChannel channel : channels) {
            if (types.stream().anyMatch(t -> channel.getType().equalsIgnoreCase(t))) {
                sendToChannel(channel, rule, message);
            }
        }
    }

    /**
     * 注册一个通知渠道.
     *
     * <p>如果已存在同类型渠道，则不会重复注册。</p>
     *
     * @param channel 通知渠道实现
     */
    public void registerChannel(AlertChannel channel) {
        if (channel == null) return;
        // 检查是否已存在同类型渠道
        boolean exists = channels.stream()
                .anyMatch(c -> c.getType().equalsIgnoreCase(channel.getType()));
        if (!exists) {
            channels.add(channel);
            log.info("[Monitor-Alert] 注册告警渠道: {}", channel.getType());
        }
    }

    /**
     * 移除指定类型的通知渠道.
     *
     * @param channelType 渠道类型（如 dingtalk、wechat、mail）
     * @return {@code true} 如果成功移除
     */
    public boolean removeChannel(String channelType) {
        return channels.removeIf(c -> c.getType().equalsIgnoreCase(channelType));
    }

    /**
     * 获取所有已注册的渠道类型.
     *
     * @return 渠道类型列表（去重）
     */
    public List<String> getChannelTypes() {
        List<String> types = new ArrayList<>();
        for (AlertChannel channel : channels) {
            types.add(channel.getType());
        }
        return types;
    }

    // ======================== 内部方法 ========================

    /**
     * 向单个渠道发送告警.
     *
     * @param channel 通知渠道
     * @param rule    告警规则
     * @param message 告警消息
     */
    private void sendToChannel(AlertChannel channel, AlertRule rule, String message) {
        try {
            String title = "[" + rule.getLevel() + "] " + rule.getName();
            channel.send(title, message);
            log.info("[Monitor-Alert] 告警已通过 {} 渠道发送: {}", channel.getType(), title);
        } catch (Exception e) {
            log.error("[Monitor-Alert] 通过 {} 渠道发送告警失败: {}", channel.getType(), e.getMessage(), e);
        }
    }

    // ======================== 内部接口：AlertChannel ========================

    /**
     * 告警通知渠道接口.
     *
     * <p>所有告警通知渠道（钉钉、企业微信、邮件等）都需要实现此接口。</p>
     */
    public interface AlertChannel {

        /**
         * 发送告警通知.
         *
         * @param title   告警标题
         * @param message 告警消息内容
         */
        void send(String title, String message);

        /**
         * 获取渠道类型标识.
         *
         * @return 渠道类型，如 {@code "dingtalk"}、{@code "wechat"}、{@code "mail"}
         */
        String getType();
    }
}

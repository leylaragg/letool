package com.github.leyland.letool.monitor.alert;

import com.github.leyland.letool.monitor.config.MonitorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 企业微信（WeChat Work）告警通知渠道.
 *
 * <p>基于企业微信群机器人 Webhook 发送文本格式的告警消息。
 * 需要在 {@code letool.monitor.alert.wechat.webhook-url} 中配置 Webhook 地址。</p>
 *
 * <p>当前版本为轻量级占位实现（日志输出），完整实现需引入 HTTP 客户端发送 POST 请求。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public class WechatNotifier implements AlertNotifier.AlertChannel {

    // ======================== 日志 ========================

    private static final Logger log = LoggerFactory.getLogger(WechatNotifier.class);

    // ======================== 字段 ========================

    /** 监控模块配置属性 */
    private final MonitorProperties properties;

    // ======================== 构造方法 ========================

    /**
     * 创建企业微信告警通知渠道.
     *
     * @param properties 监控模块配置属性
     */
    public WechatNotifier(MonitorProperties properties) {
        this.properties = properties;
    }

    // ======================== AlertChannel 实现 ========================

    /**
     * 发送企业微信告警通知.
     *
     * <p>将告警内容格式化为文本消息并通过 Webhook 发送。
     * 当前版本为占位实现，仅输出日志。</p>
     *
     * @param title   告警标题
     * @param message 告警消息内容
     */
    @Override
    public void send(String title, String message) {
        MonitorProperties.Alert.Wechat wechat = properties.getAlert().getWechat();
        String webhookUrl = wechat.getWebhookUrl();

        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.warn("[Monitor-Alert-Wechat] 未配置 webhook-url，跳过企业微信告警发送");
            return;
        }

        // 构建文本格式消息
        String textContent = String.format(
                "%s\n\n%s\n\n发送时间: %s",
                title,
                message,
                java.time.LocalDateTime.now().toString().replace("T", " ")
        );

        log.warn("[Monitor-Alert-Wechat] >>> 模拟企业微信告警发送 <<<\n"
                        + "  Webhook: {}\n"
                        + "  消息内容:\n{}",
                webhookUrl, textContent);

        // TODO: 实现实际的 HTTP POST 请求到企业微信 Webhook
        //  - 构建请求体: {"msgtype":"text","text":{"content":"..."}}
        //  - 检查响应 errcode == 0 判断发送成功
    }

    @Override
    public String getType() {
        return "wechat";
    }
}

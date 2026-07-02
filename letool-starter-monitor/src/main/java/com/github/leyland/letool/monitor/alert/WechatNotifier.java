package com.github.leyland.letool.monitor.alert;

import com.github.leyland.letool.monitor.config.MonitorProperties;
import com.github.leyland.letool.tool.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 企业微信（WeChat Work）告警通知渠道.
 *
 * <p>基于企业微信群机器人 Webhook 发送文本格式的告警消息。
 * 需要在 {@code letool.monitor.alert.wechat.webhook-url} 中配置 Webhook 地址。</p>
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
     * <p>将告警内容格式化为文本消息并通过 Webhook 发送。</p>
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

        String payload = JsonUtil.toJsonString(Map.of(
                "msgtype", "text",
                "text", Map.of("content", textContent)
        ));
        WebhookAlertClient.postJson("WeChat Work", webhookUrl, payload);
        log.info("[Monitor-Alert-Wechat] 企业微信告警发送成功: title={}", title);
    }

    @Override
    public String getType() {
        return "wechat";
    }
}

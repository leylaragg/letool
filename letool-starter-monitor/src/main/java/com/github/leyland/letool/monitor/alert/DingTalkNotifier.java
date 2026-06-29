package com.github.leyland.letool.monitor.alert;

import com.github.leyland.letool.monitor.config.MonitorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 钉钉（DingTalk）告警通知渠道.
 *
 * <p>基于钉钉自定义机器人 Webhook 发送 Markdown 格式的告警消息。
 * 需要在 {@code letool.monitor.alert.dingtalk.webhook-url} 中配置 Webhook 地址，
 * 可选配置 {@code secret} 进行加签安全校验。</p>
 *
 * <p>当前版本为轻量级占位实现（日志输出），完整实现需引入 HTTP 客户端发送 POST 请求。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public class DingTalkNotifier implements AlertNotifier.AlertChannel {

    // ======================== 日志 ========================

    private static final Logger log = LoggerFactory.getLogger(DingTalkNotifier.class);

    // ======================== 字段 ========================

    /** 监控模块配置属性 */
    private final MonitorProperties properties;

    // ======================== 构造方法 ========================

    /**
     * 创建钉钉告警通知渠道.
     *
     * @param properties 监控模块配置属性
     */
    public DingTalkNotifier(MonitorProperties properties) {
        this.properties = properties;
    }

    // ======================== AlertChannel 实现 ========================

    /**
     * 发送钉钉告警通知.
     *
     * <p>将告警内容格式化为 Markdown 消息并通过 Webhook 发送。
     * 当前版本为占位实现，仅输出日志。</p>
     *
     * @param title   告警标题
     * @param message 告警消息内容
     */
    @Override
    public void send(String title, String message) {
        MonitorProperties.Alert.DingTalk dingTalk = properties.getAlert().getDingtalk();
        String webhookUrl = dingTalk.getWebhookUrl();

        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.warn("[Monitor-Alert-DingTalk] 未配置 webhook-url，跳过钉钉告警发送");
            return;
        }

        // 构建 Markdown 格式消息
        String markdownContent = String.format(
                "## %s\n\n> 级别: %s\n\n%s\n\n---\n*发送时间: %s*",
                title,
                title.startsWith("[CRITICAL]") ? "严重" : "警告",
                message,
                java.time.LocalDateTime.now().toString().replace("T", " ")
        );

        log.warn("[Monitor-Alert-DingTalk] >>> 模拟钉钉告警发送 <<<\n"
                        + "  Webhook: {}\n"
                        + "  Secret: {}\n"
                        + "  消息内容:\n{}",
                webhookUrl,
                dingTalk.getSecret() != null ? "***已配置***" : "未配置",
                markdownContent);

        // TODO: 实现实际的 HTTP POST 请求到钉钉 Webhook
        //  - 构建请求体: {"msgtype":"markdown","markdown":{"title":"...","text":"..."}}
        //  - 如有 secret，计算签名并追加 timestamp/sign 参数
        //  - 检查响应 code == 0 判断发送成功
    }

    @Override
    public String getType() {
        return "dingtalk";
    }
}

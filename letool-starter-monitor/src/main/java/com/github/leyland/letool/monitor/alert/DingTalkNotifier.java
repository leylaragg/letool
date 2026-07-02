package com.github.leyland.letool.monitor.alert;

import com.github.leyland.letool.monitor.config.MonitorProperties;
import com.github.leyland.letool.monitor.exception.MonitorException;
import com.github.leyland.letool.tool.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * 钉钉（DingTalk）告警通知渠道.
 *
 * <p>基于钉钉自定义机器人 Webhook 发送 Markdown 格式的告警消息。
 * 需要在 {@code letool.monitor.alert.dingtalk.webhook-url} 中配置 Webhook 地址，
 * 可选配置 {@code secret} 进行加签安全校验。</p>
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
     * <p>将告警内容格式化为 Markdown 消息并通过 Webhook 发送。</p>
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

        String signedWebhookUrl = appendSignIfNecessary(webhookUrl, dingTalk.getSecret());
        String payload = JsonUtil.toJsonString(Map.of(
                "msgtype", "markdown",
                "markdown", Map.of("title", title, "text", markdownContent)
        ));
        WebhookAlertClient.postJson("DingTalk", signedWebhookUrl, payload);
        log.info("[Monitor-Alert-DingTalk] 钉钉告警发送成功: title={}, secretConfigured={}",
                title, dingTalk.getSecret() != null);
    }

    @Override
    public String getType() {
        return "dingtalk";
    }

    /**
     * 在启用钉钉加签时追加 timestamp/sign 参数。
     *
     * @param webhookUrl 原始 Webhook 地址
     * @param secret     钉钉机器人签名密钥
     * @return 原始地址或追加签名参数后的地址
     */
    private String appendSignIfNecessary(String webhookUrl, String secret) {
        if (secret == null || secret.isBlank()) {
            return webhookUrl;
        }
        long timestamp = System.currentTimeMillis();
        String sign = sign(timestamp, secret);
        String separator = webhookUrl.contains("?")
                ? (webhookUrl.endsWith("?") || webhookUrl.endsWith("&") ? "" : "&")
                : "?";
        return webhookUrl + separator + "timestamp=" + timestamp + "&sign="
                + URLEncoder.encode(sign, StandardCharsets.UTF_8);
    }

    /**
     * 计算钉钉机器人 HMAC-SHA256 签名。
     *
     * @param timestamp 当前毫秒时间戳
     * @param secret    钉钉机器人签名密钥
     * @return Base64 编码后的签名
     */
    private String sign(long timestamp, String secret) {
        try {
            String stringToSign = timestamp + "\n" + secret;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signData);
        } catch (Exception e) {
            throw new MonitorException("DingTalk webhook sign failed", e);
        }
    }
}

package com.github.leyland.letool.monitor.alert;

import com.alibaba.fastjson2.JSONObject;
import com.github.leyland.letool.monitor.exception.MonitorErrorCode;
import com.github.leyland.letool.monitor.exception.MonitorException;
import com.github.leyland.letool.tool.util.JsonUtil;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Webhook 告警 HTTP 客户端。
 *
 * <p>封装钉钉、企业微信机器人通知共用的 JSON POST、超时和响应校验逻辑，
 * 避免各通知渠道重复处理 HTTP 细节。</p>
 */
final class WebhookAlertClient {

    /** Webhook 连接超时。 */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

    /** 单次 Webhook 请求超时。 */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    /** 复用 JDK HttpClient，避免每次发送告警都创建连接池。 */
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    private WebhookAlertClient() {
    }

    /**
     * 发送 JSON Webhook 请求，并校验通用响应结构。
     *
     * @param channelName 告警渠道名称，用于错误消息
     * @param webhookUrl  Webhook 地址
     * @param payload     JSON 请求体
    * @throws MonitorException HTTP 失败、响应非 2xx 或业务错误码非 0 时抛出
     */
    static void postJson(String channelName, String webhookUrl, String payload) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(webhookUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = CLIENT.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());
            validateResponse(channelName, response);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw MonitorException.causedBy(
                    MonitorErrorCode.WEBHOOK_DELIVERY_FAILED,
                    exception,
                    channelName);
        } catch (MonitorException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw MonitorException.causedBy(
                    MonitorErrorCode.WEBHOOK_DELIVERY_FAILED,
                    exception,
                    channelName);
        }
    }

    /**
     * 校验 Webhook HTTP 状态和通用业务响应。
     *
     * @param channelName 告警渠道名称
     * @param response HTTP 响应
     * @throws MonitorException HTTP 或业务响应表示投递失败时抛出
     */
    private static void validateResponse(
            String channelName,
            HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw MonitorException.of(
                    MonitorErrorCode.WEBHOOK_DELIVERY_FAILED,
                    channelName + " HTTP " + response.statusCode());
        }

        JSONObject body = JsonUtil.parseObject(response.body());
        if (body == null) {
            throw MonitorException.of(
                    MonitorErrorCode.WEBHOOK_DELIVERY_FAILED,
                    channelName + " 返回空响应");
        }

        Integer errCode = body.getInteger("errcode");
        if (errCode != null && errCode != 0) {
            String errMsg = body.getString("errmsg");
            String detail = channelName + " errcode=" + errCode
                    + (errMsg == null || errMsg.isBlank()
                    ? ""
                    : ", errmsg=" + errMsg);
            throw MonitorException.of(
                    MonitorErrorCode.WEBHOOK_DELIVERY_FAILED,
                    detail);
        }
    }
}

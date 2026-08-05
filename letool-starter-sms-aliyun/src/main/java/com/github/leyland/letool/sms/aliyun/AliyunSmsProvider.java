package com.github.leyland.letool.sms.aliyun;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.dysmsapi20170525.models.SendSmsResponseBody;
import com.github.leyland.letool.sms.core.SmsProvider;
import com.github.leyland.letool.sms.exception.SmsErrorCode;
import com.github.leyland.letool.sms.exception.SmsException;
import com.github.leyland.letool.sms.model.SmsRecipientResult;
import com.github.leyland.letool.sms.model.SmsRequest;
import com.github.leyland.letool.sms.model.SmsResult;
import com.github.leyland.letool.tool.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * 基于阿里云短信 V2 官方 SDK 的 Provider。
 */
public final class AliyunSmsProvider implements SmsProvider {

    /** 阿里云 Provider 名称。 */
    public static final String PROVIDER_NAME = "aliyun";

    private static final Logger log = LoggerFactory.getLogger(AliyunSmsProvider.class);

    private final Client client;
    private final AliyunSmsProperties properties;

    /**
     * 创建阿里云短信 Provider。
     *
     * @param client 可复用的阿里云短信客户端
     * @param properties 阿里云短信配置
     */
    public AliyunSmsProvider(Client client, AliyunSmsProperties properties) {
        this.client = Objects.requireNonNull(client, "client 不能为空");
        this.properties = Objects.requireNonNull(properties, "properties 不能为空");
    }

    /**
     * 调用阿里云 SendSms API 发送单条或同模板批量短信。
     *
     * @param request 公共短信请求
     * @return 结构化发送结果
     */
    @Override
    public SmsResult send(SmsRequest request) {
        Objects.requireNonNull(request, "request 不能为空");
        String signName = request.getSignName() == null
                ? requireText(properties.getSignName(), "letool.sms.aliyun.sign-name")
                : request.getSignName();
        SendSmsRequest sdkRequest = new SendSmsRequest()
                .setPhoneNumbers(String.join(",", request.getPhones()))
                .setSignName(signName)
                .setTemplateCode(request.getTemplateCode())
                .setTemplateParam(JsonUtil.toJsonString(request.getNamedParameters()));
        try {
            SendSmsResponse response = client.sendSms(sdkRequest);
            SendSmsResponseBody body = requireBody(response);
            boolean success = "OK".equalsIgnoreCase(body.getCode());
            List<SmsRecipientResult> recipients = request.getPhones().stream()
                    .map(phone -> success
                            ? SmsRecipientResult.success(phone, body.getCode(), body.getMessage())
                            : SmsRecipientResult.failure(phone, body.getCode(), body.getMessage()))
                    .toList();
            if (!success) {
                log.warn(
                        "阿里云短信请求被拒绝 | recipientCount={} | templateCode={} | requestId={} | code={}",
                        request.getPhones().size(),
                        request.getTemplateCode(),
                        body.getRequestId(),
                        body.getCode());
            }
            return SmsResult.fromRecipients(
                    PROVIDER_NAME,
                    body.getRequestId(),
                    body.getCode(),
                    body.getMessage(),
                    recipients);
        } catch (SmsException exception) {
            throw exception;
        } catch (Exception exception) {
            throw SmsException.causedBy(SmsErrorCode.SEND_FAILED, exception, PROVIDER_NAME);
        }
    }

    /**
     * 获取 Provider 名称。
     *
     * @return 固定返回 {@code aliyun}
     */
    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    /**
     * 校验并提取阿里云响应体。
     *
     * @param response 阿里云 SDK 响应
     * @return 非空响应体
     */
    private SendSmsResponseBody requireBody(SendSmsResponse response) {
        if (response == null || response.getBody() == null) {
            throw SmsException.of(SmsErrorCode.SEND_FAILED, PROVIDER_NAME);
        }
        return response.getBody();
    }

    /**
     * 校验必填配置文本。
     *
     * @param value 配置值
     * @param propertyName 配置项名称
     * @return 已校验配置值
     */
    private String requireText(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw SmsException.of(SmsErrorCode.CONFIGURATION_INVALID, propertyName + " 不能为空");
        }
        return value;
    }
}

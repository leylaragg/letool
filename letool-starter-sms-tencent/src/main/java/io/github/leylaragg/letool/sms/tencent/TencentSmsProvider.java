package io.github.leylaragg.letool.sms.tencent;

import io.github.leylaragg.letool.sms.core.SmsProvider;
import io.github.leylaragg.letool.sms.exception.SmsErrorCode;
import io.github.leylaragg.letool.sms.exception.SmsException;
import io.github.leylaragg.letool.sms.model.SmsRecipientResult;
import io.github.leylaragg.letool.sms.model.SmsRequest;
import io.github.leylaragg.letool.sms.model.SmsResult;
import com.tencentcloudapi.sms.v20210111.SmsClient;
import com.tencentcloudapi.sms.v20210111.models.SendSmsRequest;
import com.tencentcloudapi.sms.v20210111.models.SendSmsResponse;
import com.tencentcloudapi.sms.v20210111.models.SendStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 基于腾讯云 SMS 3.0 官方 SDK 的 Provider。
 */
public final class TencentSmsProvider implements SmsProvider {

    /** 腾讯云 Provider 名称。 */
    public static final String PROVIDER_NAME = "tencent";

    private static final int MAX_RECIPIENTS = 200;
    private static final Pattern E164_PATTERN = Pattern.compile("^\\+[1-9]\\d{7,14}$");
    private static final Pattern DIGITS_PATTERN = Pattern.compile("^[0-9]+$");
    private static final Logger log = LoggerFactory.getLogger(TencentSmsProvider.class);

    private final SmsClient client;
    private final TencentSmsProperties properties;

    /**
     * 创建腾讯云短信 Provider。
     *
     * @param client 可复用的腾讯云短信客户端
     * @param properties 腾讯云短信配置
     */
    public TencentSmsProvider(SmsClient client, TencentSmsProperties properties) {
        this.client = Objects.requireNonNull(client, "client 不能为空");
        this.properties = Objects.requireNonNull(properties, "properties 不能为空");
    }

    /**
     * 调用腾讯云 SendSms API 发送单条或批量短信。
     *
     * @param request 公共短信请求
     * @return 结构化发送结果
     */
    @Override
    public SmsResult send(SmsRequest request) {
        Objects.requireNonNull(request, "request 不能为空");
        if (request.getPhones().size() > MAX_RECIPIENTS) {
            throw SmsException.of(
                    SmsErrorCode.REQUEST_INVALID,
                    "腾讯云单次请求最多支持 " + MAX_RECIPIENTS + " 个手机号");
        }
        String signName = request.getSignName() == null
                ? requireText(properties.getSignName(), "letool.sms.tencent.sign-name")
                : request.getSignName();
        String[] phones = request.getPhones().stream().map(this::normalizePhone).toArray(String[]::new);
        SendSmsRequest sdkRequest = new SendSmsRequest();
        sdkRequest.setPhoneNumberSet(phones);
        sdkRequest.setSmsSdkAppId(requireText(properties.getSdkAppId(), "letool.sms.tencent.sdk-app-id"));
        sdkRequest.setSignName(signName);
        sdkRequest.setTemplateId(request.getTemplateCode());
        sdkRequest.setTemplateParamSet(request.getParameterValues().toArray(String[]::new));
        try {
            SendSmsResponse response = client.SendSms(sdkRequest);
            SendStatus[] statuses = requireStatuses(response);
            List<SmsRecipientResult> recipients = Arrays.stream(statuses)
                    .map(this::toRecipientResult)
                    .toList();
            boolean success = recipients.stream().allMatch(SmsRecipientResult::isSuccess);
            String code = success ? "OK" : "PARTIAL_FAILURE";
            String message = success ? "发送成功" : "部分或全部手机号发送失败";
            if (!success) {
                log.warn(
                        "腾讯云短信请求存在失败 | recipientCount={} | templateCode={} | requestId={}",
                        request.getPhones().size(),
                        request.getTemplateCode(),
                        response.getRequestId());
            }
            return SmsResult.fromRecipients(
                    PROVIDER_NAME,
                    response.getRequestId(),
                    code,
                    message,
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
     * @return 固定返回 {@code tencent}
     */
    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    /**
     * 将手机号标准化为腾讯云要求的 E.164 格式。
     *
     * @param phone 原始手机号
     * @return E.164 手机号
     */
    private String normalizePhone(String phone) {
        String normalized = phone;
        if (DIGITS_PATTERN.matcher(phone).matches()) {
            normalized = "+" + requireText(
                    properties.getDefaultCountryCode(),
                    "letool.sms.tencent.default-country-code") + phone;
        }
        if (!E164_PATTERN.matcher(normalized).matches()) {
            throw SmsException.of(SmsErrorCode.REQUEST_INVALID, "腾讯云手机号必须符合 E.164 格式");
        }
        return normalized;
    }

    /**
     * 校验腾讯云响应状态数组。
     *
     * @param response 腾讯云响应
     * @return 非空状态数组
     */
    private SendStatus[] requireStatuses(SendSmsResponse response) {
        if (response == null || response.getSendStatusSet() == null || response.getSendStatusSet().length == 0) {
            throw SmsException.of(SmsErrorCode.SEND_FAILED, PROVIDER_NAME);
        }
        return response.getSendStatusSet();
    }

    /**
     * 转换单个腾讯云手机号状态。
     *
     * @param status 腾讯云手机号状态
     * @return 公共手机号结果
     */
    private SmsRecipientResult toRecipientResult(SendStatus status) {
        if (status == null || status.getPhoneNumber() == null || status.getPhoneNumber().isBlank()) {
            throw SmsException.of(SmsErrorCode.SEND_FAILED, PROVIDER_NAME);
        }
        if ("Ok".equalsIgnoreCase(status.getCode())) {
            return SmsRecipientResult.success(status.getPhoneNumber(), status.getCode(), status.getMessage());
        }
        return SmsRecipientResult.failure(status.getPhoneNumber(), status.getCode(), status.getMessage());
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

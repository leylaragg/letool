package io.github.leylaragg.letool.sms.tencent;

import io.github.leylaragg.letool.sms.exception.SmsException;
import io.github.leylaragg.letool.sms.model.SmsRequest;
import io.github.leylaragg.letool.sms.model.SmsResult;
import com.tencentcloudapi.sms.v20210111.SmsClient;
import com.tencentcloudapi.sms.v20210111.models.SendSmsRequest;
import com.tencentcloudapi.sms.v20210111.models.SendSmsResponse;
import com.tencentcloudapi.sms.v20210111.models.SendStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TencentSmsProvider} 官方 SDK 请求映射测试。
 */
class TencentSmsProviderTest {

    /**
     * 验证公共请求会映射为腾讯云 SendSms 请求并保持参数顺序。
     *
     * @throws Exception SDK 方法声明的受检异常
     */
    @Test
    void shouldMapOrderedParametersAndParseResponse() throws Exception {
        SmsClient client = mock(SmsClient.class);
        SendStatus status = status("+8613800138000", "Ok", "send success");
        SendSmsResponse response = response("request-1", status);
        when(client.SendSms(any(SendSmsRequest.class))).thenReturn(response);
        TencentSmsProperties properties = new TencentSmsProperties();
        properties.setSdkAppId("1400000000");
        properties.setSignName("默认签名");
        TencentSmsProvider provider = new TencentSmsProvider(client, properties);

        SmsResult result = provider.send(SmsRequest.builder()
                .phone("+8613800138000")
                .templateCode("123456")
                .parameter("code", "1234")
                .parameter("minutes", "5")
                .build());

        ArgumentCaptor<SendSmsRequest> captor = ArgumentCaptor.forClass(SendSmsRequest.class);
        verify(client).SendSms(captor.capture());
        assertThat(captor.getValue().getPhoneNumberSet()).containsExactly("+8613800138000");
        assertThat(captor.getValue().getTemplateParamSet()).containsExactly("1234", "5");
        assertThat(captor.getValue().getSmsSdkAppId()).isEqualTo("1400000000");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRequestId()).isEqualTo("request-1");
    }

    /**
     * 验证腾讯云逐手机号响应能够表达部分失败。
     *
     * @throws Exception SDK 方法声明的受检异常
     */
    @Test
    void shouldRepresentPartialFailure() throws Exception {
        SmsClient client = mock(SmsClient.class);
        SendStatus success = status("+8613800138000", "Ok", "send success");
        SendStatus failure = status(
                "+8613900139000",
                "LimitExceeded.PhoneNumberDailyLimit",
                "limit exceeded");
        when(client.SendSms(any(SendSmsRequest.class))).thenReturn(response("request-2", success, failure));
        TencentSmsProperties properties = new TencentSmsProperties();
        properties.setSdkAppId("1400000000");
        properties.setSignName("默认签名");
        TencentSmsProvider provider = new TencentSmsProvider(client, properties);

        SmsResult result = provider.send(SmsRequest.builder()
                .phones(java.util.List.of("+8613800138000", "+8613900139000"))
                .templateCode("123456")
                .build());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getRecipientResults()).extracting(item -> item.isSuccess())
                .containsExactly(true, false);
    }

    /**
     * 验证腾讯云 SDK 异常会转换为统一短信异常。
     *
     * @throws Exception SDK 方法声明的受检异常
     */
    @Test
    void shouldWrapSdkException() throws Exception {
        SmsClient client = mock(SmsClient.class);
        RuntimeException cause = new RuntimeException("network");
        when(client.SendSms(any(SendSmsRequest.class))).thenThrow(cause);
        TencentSmsProperties properties = new TencentSmsProperties();
        properties.setSdkAppId("1400000000");
        properties.setSignName("默认签名");
        TencentSmsProvider provider = new TencentSmsProvider(client, properties);

        assertThatThrownBy(() -> provider.send(SmsRequest.builder()
                .phone("+8613800138000")
                .templateCode("123456")
                .build()))
                .isInstanceOf(SmsException.class)
                .hasCause(cause);
    }

    /**
     * 创建腾讯云逐手机号状态。
     *
     * @param phone 手机号
     * @param code 结果码
     * @param message 结果说明
     * @return 腾讯云状态
     */
    private SendStatus status(String phone, String code, String message) {
        SendStatus status = new SendStatus();
        status.setPhoneNumber(phone);
        status.setCode(code);
        status.setMessage(message);
        return status;
    }

    /**
     * 创建腾讯云发送响应。
     *
     * @param requestId 请求 ID
     * @param statuses 逐手机号状态
     * @return 腾讯云发送响应
     */
    private SendSmsResponse response(String requestId, SendStatus... statuses) {
        SendSmsResponse response = new SendSmsResponse();
        response.setRequestId(requestId);
        response.setSendStatusSet(statuses);
        return response;
    }
}

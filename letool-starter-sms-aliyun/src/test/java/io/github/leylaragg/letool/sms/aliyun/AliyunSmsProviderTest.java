package io.github.leylaragg.letool.sms.aliyun;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.dysmsapi20170525.models.SendSmsResponseBody;
import io.github.leylaragg.letool.sms.exception.SmsException;
import io.github.leylaragg.letool.sms.model.SmsRequest;
import io.github.leylaragg.letool.sms.model.SmsResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AliyunSmsProvider} 官方 SDK 请求映射测试。
 */
class AliyunSmsProviderTest {

    /**
     * 验证公共请求会映射为阿里云 SendSms 请求。
     *
     * @throws Exception SDK 方法声明的受检异常
     */
    @Test
    void shouldMapRequestAndParseSuccessfulResponse() throws Exception {
        Client client = mock(Client.class);
        SendSmsResponse response = new SendSmsResponse().setBody(new SendSmsResponseBody()
                .setCode("OK")
                .setMessage("OK")
                .setRequestId("request-1")
                .setBizId("biz-1"));
        when(client.sendSms(any(SendSmsRequest.class))).thenReturn(response);
        AliyunSmsProperties properties = new AliyunSmsProperties();
        properties.setSignName("默认签名");
        AliyunSmsProvider provider = new AliyunSmsProvider(client, properties);

        SmsResult result = provider.send(SmsRequest.builder()
                .phone("13800138000")
                .templateCode("SMS_VERIFY")
                .parameter("code", "1234")
                .build());

        ArgumentCaptor<SendSmsRequest> captor = ArgumentCaptor.forClass(SendSmsRequest.class);
        verify(client).sendSms(captor.capture());
        assertThat(captor.getValue().getPhoneNumbers()).isEqualTo("13800138000");
        assertThat(captor.getValue().getSignName()).isEqualTo("默认签名");
        assertThat(captor.getValue().getTemplateCode()).isEqualTo("SMS_VERIFY");
        assertThat(captor.getValue().getTemplateParam()).contains("\"code\":\"1234\"");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getProvider()).isEqualTo("aliyun");
        assertThat(result.getRequestId()).isEqualTo("request-1");
    }

    /**
     * 验证阿里云业务错误会转换为结构化失败结果。
     *
     * @throws Exception SDK 方法声明的受检异常
     */
    @Test
    void shouldReturnFailureWhenAliyunRejectsRequest() throws Exception {
        Client client = mock(Client.class);
        when(client.sendSms(any(SendSmsRequest.class))).thenReturn(new SendSmsResponse()
                .setBody(new SendSmsResponseBody()
                        .setCode("isv.SMS_TEMPLATE_ILLEGAL")
                        .setMessage("模板不合法")
                        .setRequestId("request-2")));
        AliyunSmsProperties properties = new AliyunSmsProperties();
        properties.setSignName("默认签名");
        AliyunSmsProvider provider = new AliyunSmsProvider(client, properties);

        SmsResult result = provider.send(SmsRequest.builder()
                .phone("13800138000")
                .templateCode("SMS_VERIFY")
                .build());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo("isv.SMS_TEMPLATE_ILLEGAL");
    }

    /**
     * 验证 SDK 异常会转换为保留原因链的统一短信异常。
     *
     * @throws Exception SDK 方法声明的受检异常
     */
    @Test
    void shouldWrapSdkException() throws Exception {
        Client client = mock(Client.class);
        RuntimeException cause = new RuntimeException("network");
        when(client.sendSms(any(SendSmsRequest.class))).thenThrow(cause);
        AliyunSmsProperties properties = new AliyunSmsProperties();
        properties.setSignName("默认签名");
        AliyunSmsProvider provider = new AliyunSmsProvider(client, properties);

        assertThatThrownBy(() -> provider.send(SmsRequest.builder()
                .phone("13800138000")
                .templateCode("SMS_VERIFY")
                .build()))
                .isInstanceOf(SmsException.class)
                .hasCause(cause);
    }
}

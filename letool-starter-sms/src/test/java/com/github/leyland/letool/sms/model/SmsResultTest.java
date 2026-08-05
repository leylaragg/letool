package com.github.leyland.letool.sms.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SmsResult} 不可变结果测试。
 */
class SmsResultTest {

    /**
     * 验证全部手机号成功时整体成功。
     */
    @Test
    void shouldBeSuccessfulWhenAllRecipientsSucceed() {
        SmsResult result = SmsResult.fromRecipients(
                "aliyun",
                "request-1",
                "OK",
                "发送成功",
                List.of(SmsRecipientResult.success("+8613800138000", "OK", "成功")));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getErrorCode()).isNull();
        assertThat(result.getProvider()).isEqualTo("aliyun");
    }

    /**
     * 验证任一手机号失败时整体失败。
     */
    @Test
    void shouldFailWhenAnyRecipientFails() {
        SmsResult result = SmsResult.fromRecipients(
                "tencent",
                "request-2",
                "PARTIAL_FAILURE",
                "部分失败",
                List.of(
                        SmsRecipientResult.success("+8613800138000", "Ok", "成功"),
                        SmsRecipientResult.failure("+8613900139000", "Failed", "失败")));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("PARTIAL_FAILURE");
        assertThat(result.getRecipientResults()).hasSize(2);
    }

    /**
     * 验证结果复制调用方列表。
     */
    @Test
    void shouldCopyRecipientResults() {
        List<SmsRecipientResult> source = new ArrayList<>();
        source.add(SmsRecipientResult.success("+8613800138000", "OK", "成功"));
        SmsResult result = SmsResult.fromRecipients("mock", "request", "OK", "成功", source);
        source.clear();

        assertThat(result.getRecipientResults()).hasSize(1);
        assertThatThrownBy(() -> result.getRecipientResults().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * 验证安全诊断文本不包含手机号和模板内容。
     */
    @Test
    void shouldNotExposePhoneInToString() {
        SmsResult result = SmsResult.fromRecipients(
                "mock",
                "request",
                "OK",
                "成功",
                List.of(SmsRecipientResult.success("+8613800138000", "OK", "成功")));

        assertThat(result.toString()).doesNotContain("+8613800138000");
    }
}

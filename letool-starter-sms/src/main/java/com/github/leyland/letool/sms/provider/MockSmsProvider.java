package com.github.leyland.letool.sms.provider;

import com.github.leyland.letool.sms.core.SmsProvider;
import com.github.leyland.letool.sms.model.SmsRecipientResult;
import com.github.leyland.letool.sms.model.SmsRequest;
import com.github.leyland.letool.sms.model.SmsResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 仅用于开发和测试的内存短信 Provider。
 */
public final class MockSmsProvider implements SmsProvider {

    /** Mock Provider 名称。 */
    public static final String PROVIDER_NAME = "mock";

    private static final Logger log = LoggerFactory.getLogger(MockSmsProvider.class);

    private final List<SentMessage> sentMessages = Collections.synchronizedList(new ArrayList<>());

    /**
     * 记录请求并返回成功结果，不产生真实短信费用。
     *
     * @param request 短信请求
     * @return 模拟成功结果
     */
    @Override
    public SmsResult send(SmsRequest request) {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        sentMessages.add(new SentMessage(request, requestId, Instant.now()));
        List<SmsRecipientResult> recipientResults = request.getPhones().stream()
                .map(phone -> SmsRecipientResult.success(phone, "OK", "Mock 发送成功"))
                .toList();
        log.debug(
                "Mock 短信发送完成 | recipientCount={} | templateCode={} | requestId={}",
                request.getPhones().size(),
                request.getTemplateCode(),
                requestId);
        return SmsResult.fromRecipients(PROVIDER_NAME, requestId, "OK", "Mock 发送成功", recipientResults);
    }

    /**
     * 获取 Provider 名称。
     *
     * @return 固定返回 {@code mock}
     */
    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    /**
     * 获取已发送消息的不可变快照。
     *
     * @return 已发送消息快照
     */
    public List<SentMessage> getSentMessages() {
        synchronized (sentMessages) {
            return List.copyOf(sentMessages);
        }
    }

    /**
     * 清空已发送消息。
     */
    public void clearMessages() {
        sentMessages.clear();
    }

    /**
     * Mock Provider 保存的不可变发送记录。
     */
    public static final class SentMessage {

        private final SmsRequest request;
        private final String requestId;
        private final Instant sentAt;

        /**
         * 创建发送记录。
         *
         * @param request 不可变短信请求
         * @param requestId 模拟请求 ID
         * @param sentAt 记录时间
         */
        private SentMessage(SmsRequest request, String requestId, Instant sentAt) {
            this.request = request;
            this.requestId = requestId;
            this.sentAt = sentAt;
        }

        /**
         * 获取发送请求。
         *
         * @return 不可变短信请求
         */
        public SmsRequest getRequest() {
            return request;
        }

        /**
         * 获取模拟请求 ID。
         *
         * @return 请求 ID
         */
        public String getRequestId() {
            return requestId;
        }

        /**
         * 获取记录时间。
         *
         * @return 记录时间
         */
        public Instant getSentAt() {
            return sentAt;
        }
    }
}

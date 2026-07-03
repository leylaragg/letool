package com.github.leyland.letool.sms.provider;

import com.github.leyland.letool.sms.core.SmsProvider;
import com.github.leyland.letool.sms.exception.SmsException;
import com.github.leyland.letool.sms.model.SmsResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// ======================== 类级别说明 ========================

/**
 * <p>模拟短信服务提供者 — 用于单元测试和开发环境的 Mock 实现。</p>
 *
 * <h3>职责</h3>
 * <p>提供不产生实际费用的短信发送模拟实现，始终返回成功结果，
 * 并记录所有发送消息到内存列表中供测试验证。</p>
 *
 * <h3>核心特性</h3>
 * <ul>
 *   <li><b>始终成功</b>：所有发送操作均返回成功结果，不会产生实际 API 调用。</li>
 *   <li><b>消息存储</b>：所有发送的短信内容记录在内存中，可通过
 *       {@link #getSentMessages()} 方法获取用于测试断言。</li>
 *   <li><b>日志等级</b>：使用 <b>DEBUG</b> 级别记录日志，避免污染生产日志输出。</li>
 *   <li><b>线程安全</b>：消息列表使用 {@code synchronized} 保证并发场景下的安全性。</li>
 * </ul>
 *
 * <h3>典型用法</h3>
 * <pre>{@code
 * &#64;Autowired
 * private MockSmsProvider mockSmsProvider;
 *
 * &#64;Test
 * public void testSendSms() {
 *     SmsResult result = mockSmsProvider.send("13800138000", "SMS_001", Map.of("code", "1234"));
 *     assertTrue(result.isSuccess());
 *     assertEquals(1, mockSmsProvider.getSentMessages().size());
 *     assertEquals("13800138000", mockSmsProvider.getSentMessages().get(0).getPhone());
 * }
 * }</pre>
 *
 * <h3>设计说明</h3>
 * <ul>
 *   <li>仅在业务项目直接注册，或配置 {@code letool.sms.mock-enabled=true} 时，
 *       由 {@link com.github.leyland.letool.sms.config.SmsAutoConfiguration} 创建。</li>
 *   <li>支持通过 {@link #clearMessages()} 清空已发送消息记录。</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 */
public class MockSmsProvider implements SmsProvider {

    // ======================== 常量与成员变量 ========================

    private static final Logger log = LoggerFactory.getLogger(MockSmsProvider.class);

    /** 提供商标识 */
    public static final String PROVIDER_NAME = "mock";

    /** 已发送消息记录（线程安全） */
    private final List<SentMessage> sentMessages = Collections.synchronizedList(new ArrayList<>());

    // ======================== 短信发送实现 ========================

    /**
     * 模拟发送单条短信，始终返回成功结果。
     *
     * @param phone        目标手机号
     * @param templateCode 短信模板编码
     * @param params       模板变量映射
     * @return 始终返回成功的 {@link SmsResult}
     * @throws SmsException 参数校验失败时抛出
     */
    @Override
    public SmsResult send(String phone, String templateCode, Map<String, String> params) {
        // ---- 参数校验 ----
        if (phone == null || phone.isBlank()) {
            throw new SmsException("手机号不能为空");
        }
        if (templateCode == null || templateCode.isBlank()) {
            throw new SmsException("短信模板编码不能为空");
        }

        String requestId = UUID.randomUUID().toString().replace("-", "");

        // ---- 记录消息 ----
        sentMessages.add(new SentMessage(phone, templateCode, params, requestId));

        // ---- 调试日志 ----
        log.debug("[Mock短信] 模拟发送短信 | phone={} | templateCode={} | params={} | requestId={}",
                phone, templateCode, params, requestId);

        return SmsResult.success(requestId);
    }

    /**
     * 模拟批量发送短信，始终返回成功结果。
     *
     * @param phones       目标手机号列表
     * @param templateCode 短信模板编码
     * @param params       模板变量映射
     * @return 始终返回成功的 {@link SmsResult}
     * @throws SmsException 参数校验失败时抛出
     */
    @Override
    public SmsResult batchSend(List<String> phones, String templateCode, Map<String, String> params) {
        // ---- 参数校验 ----
        if (phones == null || phones.isEmpty()) {
            throw new SmsException("手机号列表不能为空");
        }
        if (templateCode == null || templateCode.isBlank()) {
            throw new SmsException("短信模板编码不能为空");
        }

        String requestId = UUID.randomUUID().toString().replace("-", "");

        // ---- 记录所有消息 ----
        for (String phone : phones) {
            sentMessages.add(new SentMessage(phone, templateCode, params, requestId));
        }

        // ---- 调试日志 ----
        log.debug("[Mock短信] 模拟批量发送短信 | phones={} | templateCode={} | params={} | requestId={}",
                phones, templateCode, params, requestId);

        return SmsResult.success(requestId);
    }

    // ======================== 提供商标识 ========================

    /**
     * 获取提供者名称。
     *
     * @return 固定返回 {@code "mock"}
     */
    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    // ======================== Mock 专用方法 ========================

    /**
     * 获取所有已发送的短信消息记录。
     *
     * <p>返回的列表为当前消息记录的<strong>快照</strong>（不可变副本），
     * 对返回列表的修改不会影响内部记录。</p>
     *
     * @return 已发送消息列表的不可变副本
     */
    public List<SentMessage> getSentMessages() {
        synchronized (sentMessages) {
            return new ArrayList<>(sentMessages);
        }
    }

    /**
     * 清空所有已发送的短信消息记录。
     *
     * <p>通常在测试用例的 {@code @BeforeEach} 或 {@code @AfterEach} 阶段调用，
     * 保证各测试之间的隔离性。</p>
     */
    public void clearMessages() {
        sentMessages.clear();
        log.debug("[Mock短信] 已清空全部消息记录");
    }

    // ======================== 内部类：已发送消息模型 ========================

    /**
     * <p>已发送短信消息模型 — 记录模拟发送的短信内容和元数据。</p>
     *
     * <h3>字段说明</h3>
     * <ul>
     *   <li><b>phone</b> — 目标手机号。</li>
     *   <li><b>templateCode</b> — 使用的短信模板编码。</li>
     *   <li><b>params</b> — 模板变量映射。</li>
     *   <li><b>requestId</b> — 模拟生成的请求 ID。</li>
     *   <li><b>sendTime</b> — 消息发送时间戳（毫秒）。</li>
     * </ul>
     */
    public static class SentMessage {

        /** 目标手机号 */
        private final String phone;

        /** 短信模板编码 */
        private final String templateCode;

        /** 模板变量映射 */
        private final Map<String, String> params;

        /** 模拟请求 ID */
        private final String requestId;

        /** 发送时间戳 */
        private final long sendTime;

        /**
         * 构造已发送消息记录。
         *
         * @param phone        目标手机号
         * @param templateCode 短信模板编码
         * @param params       模板变量映射
         * @param requestId    模拟请求 ID
         */
        SentMessage(String phone, String templateCode, Map<String, String> params, String requestId) {
            this.phone = phone;
            this.templateCode = templateCode;
            this.params = params;
            this.requestId = requestId;
            this.sendTime = System.currentTimeMillis();
        }

        // ---- Getter ----

        /**
         * 获取目标手机号。
         *
         * @return 手机号
         */
        public String getPhone() {
            return phone;
        }

        /**
         * 获取短信模板编码。
         *
         * @return 模板编码
         */
        public String getTemplateCode() {
            return templateCode;
        }

        /**
         * 获取模板变量映射。
         *
         * @return 参数映射（不可变视图）
         */
        public Map<String, String> getParams() {
            return Collections.unmodifiableMap(params);
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
         * 获取发送时间戳。
         *
         * @return 发送时间的毫秒时间戳
         */
        public long getSendTime() {
            return sendTime;
        }

        @Override
        public String toString() {
            return "SentMessage{" +
                    "phone='" + phone + '\'' +
                    ", templateCode='" + templateCode + '\'' +
                    ", params=" + params +
                    ", requestId='" + requestId + '\'' +
                    '}';
        }
    }
}

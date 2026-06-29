package com.github.leyland.letool.sms.core;

import com.github.leyland.letool.sms.config.SmsProperties;
import com.github.leyland.letool.sms.exception.SmsException;
import com.github.leyland.letool.sms.model.SmsResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// ======================== 类级别说明 ========================

/**
 * <p>短信模板 — 用户操作短信模块的<strong>核心入口类</strong>，提供 Builder 模式的链式调用 API。</p>
 *
 * <h3>设计理念</h3>
 * <p>{@code SmsTemplate} 封装了底层 {@link SmsProvider} 和频率限制逻辑，
 * 将复杂的短信发送过程抽象为直观的链式调用，同时内置发送频率保护机制。</p>
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li><b>链式构建</b>：通过内部类 {@link Builder} 逐步设置短信各项属性并发送。</li>
 *   <li><b>单条发送</b>：向单个手机号发送指定模板的短信。</li>
 *   <li><b>批量发送</b>：向多个手机号批量发送相同内容的短信。</li>
 *   <li><b>频率限制</b>：基于内存 {@code ConcurrentHashMap} 的每分钟/每天发送次数限制。</li>
 *   <li><b>多服务商切换</b>：通过配置切换底层 Provider，无需修改业务代码。</li>
 * </ul>
 *
 * <h3>典型用法 — 链式单条发送</h3>
 * <pre>{@code
 * @Autowired
 * private SmsTemplate smsTemplate;
 *
 * public void sendVerificationCode() {
 *     SmsResult result = smsTemplate.builder()
 *         .to("13800138000")
 *         .template("SMS_001")
 *         .param("code", "1234")
 *         .send();
 *
 *     if (result.isSuccess()) {
 *         log.info("短信发送成功, requestId={}", result.getRequestId());
 *     }
 * }
 * }</pre>
 *
 * <h3>典型用法 — 多样式参数</h3>
 * <pre>{@code
 * smsTemplate.builder()
 *     .to("13800138000")
 *     .template("SMS_002")
 *     .param("code", "5678")
 *     .param("product", "Ailind 企业工具包")
 *     .send();
 * }</pre>
 *
 * <h3>典型用法 — 批量发送</h3>
 * <pre>{@code
 * SmsResult result = smsTemplate.batchSend(
 *     List.of("13800138000", "13900139000"),
 *     "SMS_003",
 *     Map.of("activity", "年中大促")
 * );
 * }</pre>
 *
 * <h3>频率限制机制</h3>
 * <p>基于 {@link ConcurrentHashMap} 实现，以手机号为 key，记录最近一分钟和当天的发送计数：</p>
 * <ul>
 *   <li>超过 {@code maxPerMinute}（默认 10）时抛出 {@link SmsException}。</li>
 *   <li>超过 {@code maxPerDay}（默认 100）时抛出 {@link SmsException}。</li>
 *   <li>可通过配置 {@code letool.sms.rate-limit.enabled=false} 关闭频率限制。</li>
 * </ul>
 *
 * <h3>线程安全</h3>
 * <p>本类使用 {@code ConcurrentHashMap} 和 {@code AtomicInteger} 保证频率计数的线程安全。
 * Builder 为非线程安全对象，建议每次调用时创建新的 Builder：{@code smsTemplate.builder()...}。</p>
 *
 * <h3>异常处理</h3>
 * <ul>
 *   <li>频率超限：抛出 {@link SmsException}，建议调用方捕获并提示用户稍后重试。</li>
 *   <li>Provider 异常：由底层 Provider 抛出 {@link SmsException}，直接传播至调用方。</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 */
public class SmsTemplate {

    // ======================== 常量与成员变量 ========================

    private static final Logger log = LoggerFactory.getLogger(SmsTemplate.class);

    /** 底层短信服务提供者 */
    private final SmsProvider smsProvider;

    /** 短信配置属性 */
    private final SmsProperties properties;

    /**
     * 频率限制计数器。
     * <p>外层 Map 以手机号为 key，内层 Map 以时间窗口标识为 key，
     * 记录该时间窗口内的发送计数。</p>
     * <p>时间窗口标识格式：</p>
     * <ul>
     *   <li><b>minute:{yyyyMMddHHmm}</b> — 分钟级窗口</li>
     *   <li><b>day:{yyyyMMdd}</b> — 天级窗口</li>
     * </ul>
     */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, AtomicInteger>> rateLimitCounters = new ConcurrentHashMap<>();

    // ======================== 构造方法 ========================

    /**
     * 构造短信模板实例。
     *
     * @param smsProvider 短信服务提供者
     * @param properties  短信配置属性
     */
    public SmsTemplate(SmsProvider smsProvider, SmsProperties properties) {
        this.smsProvider = smsProvider;
        this.properties = properties;
    }

    // ======================== 公有 API：直接发送 ========================

    /**
     * 直接发送单条短信（非 Builder 方式）。
     *
     * <p>适用于已构造好参数的场景，在发送前会进行频率限制检查。</p>
     *
     * @param phone        目标手机号
     * @param templateCode 短信模板编码
     * @param params       模板变量映射
     * @return 短信发送结果
     * @throws SmsException 频率超限或发送失败时抛出
     */
    public SmsResult send(String phone, String templateCode, Map<String, String> params) {
        // ---- 频率限制检查 ----
        checkRateLimit(phone);

        // ---- 委托 Provider 发送 ----
        SmsResult result = smsProvider.send(phone, templateCode, params);

        // ---- 记录发送结果 ----
        log.info("短信发送完成 | provider={} | phone={} | templateCode={} | success={} | requestId={}",
                smsProvider.getProviderName(), phone, templateCode, result.isSuccess(), result.getRequestId());

        return result;
    }

    /**
     * 直接批量发送短信（非 Builder 方式）。
     *
     * <p>对每个手机号分别进行频率限制检查，然后委托 Provider 执行批量发送。</p>
     *
     * @param phones       目标手机号列表
     * @param templateCode 短信模板编码
     * @param params       模板变量映射
     * @return 短信发送结果
     * @throws SmsException 任一手机号频率超限或发送失败时抛出
     */
    public SmsResult batchSend(List<String> phones, String templateCode, Map<String, String> params) {
        // ---- 频率限制检查（对每个手机号独立检查） ----
        for (String phone : phones) {
            checkRateLimit(phone);
        }

        // ---- 委托 Provider 批量发送 ----
        SmsResult result = smsProvider.batchSend(phones, templateCode, params);

        // ---- 记录发送结果 ----
        log.info("批量短信发送完成 | provider={} | phoneCount={} | templateCode={} | success={} | requestId={}",
                smsProvider.getProviderName(), phones.size(), templateCode, result.isSuccess(), result.getRequestId());

        return result;
    }

    // ======================== 公有 API：Builder 入口 ========================

    /**
     * 创建短信发送构建器 — 使用 Builder 链式 API 的入口。
     *
     * <pre>{@code
     * smsTemplate.builder()
     *     .to("13800138000")
     *     .template("SMS_001")
     *     .param("code", "1234")
     *     .send();
     * }</pre>
     *
     * @return 新的 {@link Builder} 实例
     */
    public Builder builder() {
        return new Builder(this);
    }

    // ======================== 频率限制实现 ========================

    /**
     * 对指定手机号执行频率限制检查。
     *
     * <p>检查顺序：先检查分钟限制，再检查天限制。
     * 任一限制超限则立即抛出异常，不进行后续检查。</p>
     *
     * <h3>计数机制</h3>
     * <p>使用 {@link AtomicInteger} 进行原子计数，保证并发场景下的准确性。
     * 计数窗口基于系统当前时间自动计算，无需手动重置。</p>
     *
     * @param phone 目标手机号
     * @throws SmsException 当发送频率超过限制时抛出
     */
    private void checkRateLimit(String phone) {
        // ---- 频率限制未启用，直接放行 ----
        SmsProperties.RateLimit rateLimit = properties.getRateLimit();
        if (!rateLimit.isEnabled()) {
            return;
        }

        // ---- 获取或创建该手机号的计数器 ----
        ConcurrentHashMap<String, AtomicInteger> phoneCounters = rateLimitCounters.computeIfAbsent(phone, k -> new ConcurrentHashMap<>());

        // ---- 分钟级限制检查 ----
        String minuteKey = "minute:" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        int minuteCount = phoneCounters.computeIfAbsent(minuteKey, k -> new AtomicInteger(0)).incrementAndGet();
        if (minuteCount > rateLimit.getMaxPerMinute()) {
            log.warn("短信频率超限（分钟级） | phone={} | minuteCount={} | maxPerMinute={}", phone, minuteCount, rateLimit.getMaxPerMinute());
            throw new SmsException("短信发送频率超限，每分钟最多发送 " + rateLimit.getMaxPerMinute() + " 条");
        }

        // ---- 天级限制检查 ----
        String dayKey = "day:" + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        int dayCount = phoneCounters.computeIfAbsent(dayKey, k -> new AtomicInteger(0)).incrementAndGet();
        if (dayCount > rateLimit.getMaxPerDay()) {
            log.warn("短信频率超限（天级） | phone={} | dayCount={} | maxPerDay={}", phone, dayCount, rateLimit.getMaxPerDay());
            throw new SmsException("短信发送频率超限，每天最多发送 " + rateLimit.getMaxPerDay() + " 条");
        }
    }

    /**
     * 获取指定手机号的频率限制计数快照。
     *
     * <p>主要用于监控和调试，返回当前计数信息。</p>
     *
     * @param phone 目标手机号
     * @return 频率计数信息，若该手机号尚无记录则返回空 Map
     */
    public Map<String, Integer> getRateLimitSnapshot(String phone) {
        Map<String, Integer> snapshot = new HashMap<>();
        ConcurrentHashMap<String, AtomicInteger> phoneCounters = rateLimitCounters.get(phone);
        if (phoneCounters != null) {
            for (Map.Entry<String, AtomicInteger> entry : phoneCounters.entrySet()) {
                snapshot.put(entry.getKey(), entry.getValue().get());
            }
        }
        return snapshot;
    }

    // ======================== 内部类：短信发送构建器 ========================

    /**
     * <p>短信发送的 Builder 模式构建器 — 用于以链式调用的方式逐步构建并发送短信。</p>
     *
     * <h3>设计说明</h3>
     * <p>构建器通过 {@link SmsTemplate#builder()} 创建，持有对 Template 的引用，
     * 在构建完成后可直接调用 {@link #send()} 触发实际发送。</p>
     *
     * <h3>方法链约定</h3>
     * <p>除 {@link #send()} 外，所有设置方法均返回 {@code this}，
     * 实现流畅的链式调用风格。</p>
     *
     * <h3>Typical usage</h3>
     * <pre>{@code
     * SmsResult result = smsTemplate.builder()
     *     .to("13800138000")
     *     .template("SMS_001")
     *     .param("code", "1234")
     *     .send();
     * }</pre>
     *
     * @see SmsTemplate#builder()
     */
    public static class Builder {

        /** 关联的短信模板实例，用于最终触发发送 */
        private final SmsTemplate template;

        /** 目标手机号 */
        private String phone;

        /** 短信模板编码 */
        private String templateCode;

        /** 模板变量映射 */
        private final Map<String, String> params = new HashMap<>();

        /**
         * 私有构造方法，仅由外部类调用。
         *
         * @param template 短信模板实例
         */
        private Builder(SmsTemplate template) {
            this.template = template;
        }

        /**
         * 设置目标手机号。
         *
         * <p>支持单个手机号，同时支持添加多条记录（多 receiver 时对同一手机号内容使用）。</p>
         *
         * @param phone 目标手机号
         * @return 当前构建器实例（链式调用）
         */
        public Builder to(String phone) {
            this.phone = phone;
            return this;
        }

        /**
         * 设置短信模板编码。
         *
         * <p>模板编码需与短信服务商控制台中已审核通过的模板相对应。</p>
         *
         * @param templateCode 短信模板编码
         * @return 当前构建器实例（链式调用）
         */
        public Builder template(String templateCode) {
            this.templateCode = templateCode;
            return this;
        }

        /**
         * 添加单个模板变量。
         *
         * <p>可多次调用以添加多个模板变量。</p>
         *
         * @param key   变量名
         * @param value 变量值
         * @return 当前构建器实例（链式调用）
         */
        public Builder param(String key, String value) {
            this.params.put(key, value);
            return this;
        }

        /**
         * 批量添加模板变量。
         *
         * <p>一次性设置所有模板变量，会与已有变量合并（后添加的覆盖同名的）。</p>
         *
         * @param params 模板变量映射
         * @return 当前构建器实例（链式调用）
         */
        public Builder params(Map<String, String> params) {
            if (params != null) {
                this.params.putAll(params);
            }
            return this;
        }

        /**
         * 构建短信参数并发送。
         *
         * <p>此方法为链式调用的终点，触发实际的短信发送操作。</p>
         *
         * @return 短信发送结果
         * @throws SmsException 参数校验失败、频率超限或发送异常时抛出
         */
        public SmsResult send() {
            // ---- 参数校验 ----
            if (phone == null || phone.isBlank()) {
                throw new SmsException("目标手机号不能为空，请调用 .to() 方法设置");
            }
            if (templateCode == null || templateCode.isBlank()) {
                throw new SmsException("短信模板编码不能为空，请调用 .template() 方法设置");
            }

            // ---- 委托 Template 发送 ----
            return template.send(phone, templateCode, params);
        }
    }
}

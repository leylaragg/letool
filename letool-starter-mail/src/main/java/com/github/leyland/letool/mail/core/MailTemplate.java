package com.github.leyland.letool.mail.core;

import com.github.leyland.letool.mail.exception.MailException;
import com.github.leyland.letool.mail.model.MailRequest;
import com.github.leyland.letool.mail.model.MailResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// ======================== 类级别说明 ========================

/**
 * <p>邮件模板 — 用户操作邮件模块的<strong>核心入口类</strong>，提供 Builder 模式的链式调用 API。</p>
 *
 * <h3>设计理念</h3>
 * <p>{@code MailTemplate} 封装了底层 {@link MailSender} 和异步线程池，
 * 将复杂的邮件构建过程抽象为直观的链式调用，同时支持同步和异步两种发送方式。</p>
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li><b>链式构建</b>：通过内部类 {@link MailRequestBuilder} 逐步设置邮件各项属性。</li>
 *   <li><b>同步发送</b>：调用 {@link MailRequestBuilder#send()} 阻塞等待发送结果。</li>
 *   <li><b>异步发送</b>：调用 {@link MailRequestBuilder#sendAsync()} 立即返回 {@link CompletableFuture}。</li>
 *   <li><b>直接发送</b>：对于已构建好的 {@link MailRequest}，可直接调用 {@link #send(MailRequest)}。</li>
 *   <li><b>附件支持</b>：通过 {@link MailRequestBuilder#attachment(String, File)} 添加文件附件。</li>
 *   <li><b>模板变量</b>：支持向邮件模板注入变量，配合模板引擎使用。</li>
 * </ul>
 *
 * <h3>典型用法 — 同步发送纯文本邮件</h3>
 * <pre>{@code
 * @Autowired
 * private MailTemplate mailTemplate;
 *
 * public void notifyUser() {
 *     MailResponse response = mailTemplate.builder()
 *         .to("user@example.com")
 *         .subject("密码重置通知")
 *         .text("您的密码已成功重置。")
 *         .send();
 *
 *     if (response.isSuccess()) {
 *         log.info("邮件发送成功, messageId={}", response.getMessageId());
 *     }
 * }
 * }</pre>
 *
 * <h3>典型用法 — 异步发送 HTML 邮件</h3>
 * <pre>{@code
 * public void broadcastNewsletter() {
 *     List<String> recipients = List.of("user1@example.com", "user2@example.com");
 *     for (String recipient : recipients) {
 *         mailTemplate.builder()
 *             .to(recipient)
 *             .cc("archive@example.com")
 *             .subject("本周资讯")
 *             .html("<h1>本周要闻</h1><p>欢迎阅读本周资讯。</p>")
 *             .sendAsync()
 *             .thenAccept(response -> {
 *                 if (response.isSuccess()) {
 *                     log.info("已发送至: {}", recipient);
 *                 }
 *             });
 *     }
 * }
 * }</pre>
 *
 * <h3>典型用法 — 带附件与模板变量</h3>
 * <pre>{@code
 * mailTemplate.builder()
 *     .to("report@example.com")
 *     .subject("月度报告")
 *     .template("monthly-report")
 *     .variable("userName", "张三")
 *     .variable("reportDate", "2026-06")
 *     .attachment("report.pdf", new File("/path/to/report.pdf"))
 *     .send();
 * }</pre>
 *
 * <h3>典型用法 — 自定义发件人</h3>
 * <pre>{@code
 * mailTemplate.builder()
 *     .from("support@example.com", "技术支持")
 *     .to("customer@example.com")
 *     .subject("工单处理通知")
 *     .text("您的工单 #1234 已被受理。")
 *     .send();
 * }</pre>
 *
 * <h3>线程安全</h3>
 * <p>本类本身是线程安全的。内部异步执行器为固定大小线程池，
 * 多个线程共享同一个 Builder 实例可能产生竞态条件 —
 * 建议每次调用时创建新的 Builder：{@code mailTemplate.builder()...}。</p>
 *
 * <h3>异常处理</h3>
 * <ul>
 *   <li>同步发送：异常直接向上抛出（{@link MailException}）。</li>
 *   <li>异步发送：异常封装在 {@link CompletableFuture} 中，调用方需自行处理。</li>
 * </ul>
 *
 * @author leyland
 * @since 1.0.0
 */
public class MailTemplate {

    // ======================== 日志与成员变量 ========================

    private static final Logger log = LoggerFactory.getLogger(MailTemplate.class);

    /** 底层邮件发送器 */
    private final MailSender mailSender;

    /** 异步发送专用线程池 */
    private final ExecutorService asyncExecutor;

    // ======================== 构造方法 ========================

    /**
     * 构造邮件模板实例。
     *
     * @param mailSender    邮件发送器
     * @param asyncPoolSize 异步发送线程池大小
     */
    public MailTemplate(MailSender mailSender, int asyncPoolSize) {
        this.mailSender = mailSender;
        this.asyncExecutor = Executors.newFixedThreadPool(asyncPoolSize);
    }

    // ======================== 公有 API ========================

    /**
     * 创建邮件请求构建器 — 使用 Builder 链式 API 的入口。
     *
     * <pre>{@code
     * mailTemplate.builder()
     *     .to("user@example.com")
     *     .subject("Hello")
     *     .text("Content")
     *     .send();
     * }</pre>
     *
     * @return 新的 {@link MailRequestBuilder} 实例
     */
    public MailRequestBuilder builder() {
        return new MailRequestBuilder(this);
    }

    /**
     * 直接发送已构建好的邮件请求（同步阻塞）。
     *
     * <p>适用于需要手动组装 {@link MailRequest} 再发送的场景。</p>
     *
     * @param request 邮件请求
     * @return 邮件响应
     */
    public MailResponse send(MailRequest request) {
        return mailSender.send(request);
    }

    /**
     * 异步发送已构建好的邮件请求，立即返回 {@link CompletableFuture}。
     *
     * <p>发送过程在异步线程池中执行，不阻塞调用线程。</p>
     *
     * @param request 邮件请求
     * @return 包含 {@link MailResponse} 的异步结果
     */
    public CompletableFuture<MailResponse> sendAsync(MailRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return mailSender.send(request);
            } catch (Exception e) {
                log.error("Async mail send failed: {}", e.getMessage(), e);
                throw new MailException("Async mail send failed", e);
            }
        }, asyncExecutor);
    }

    // ======================== 内部类：邮件请求构建器 ========================

    /**
     * <p>邮件请求的 Builder 模式构建器 — 用于以链式调用的方式逐步构建并发送邮件。</p>
     *
     * <h3>设计说明</h3>
     * <p>构建器通过 {@link MailTemplate#builder()} 创建，持有对 Template 的引用，
     * 因此构建完成后可直接调用 {@link #send()} 或 {@link #sendAsync()} 触达底层发送器。</p>
     *
     * <h3>方法链约定</h3>
     * <p>除 {@link #send()} 和 {@link #sendAsync()} 外，所有设置方法均返回 {@code this}，
     * 实现流畅的链式调用风格。</p>
     *
     * @see MailTemplate#builder()
     */
    public static class MailRequestBuilder {

        /** 关联的邮件模板实例，用于最终触发发送 */
        private final MailTemplate template;

        /** 正在构建的邮件请求对象 */
        private final MailRequest request = new MailRequest();

        /**
         * 私有构造方法，仅由外部类调用。
         *
         * @param template 邮件模板实例
         */
        private MailRequestBuilder(MailTemplate template) {
            this.template = template;
        }

        // ---- 发件人设置 ----

        /**
         * 设置发件人邮箱地址。
         *
         * @param from 发件人邮箱地址
         * @return 当前构建器实例（链式调用）
         */
        public MailRequestBuilder from(String from) { request.setFrom(from); return this; }

        /**
         * 设置发件人邮箱地址及显示名称。
         *
         * @param from     发件人邮箱地址
         * @param personal 发件人显示名称（如"系统通知"）
         * @return 当前构建器实例（链式调用）
         */
        public MailRequestBuilder from(String from, String personal) {
            request.setFrom(from); request.setPersonal(personal); return this;
        }

        // ---- 收件人设置 ----

        /**
         * 添加收件人地址（可一次添加多个）。
         *
         * @param addresses 收件人邮箱地址数组
         * @return 当前构建器实例（链式调用）
         */
        public MailRequestBuilder to(String... addresses) { request.addTo(addresses); return this; }

        /**
         * 添加抄送地址（可一次添加多个）。
         *
         * @param addresses 抄送邮箱地址数组
         * @return 当前构建器实例（链式调用）
         */
        public MailRequestBuilder cc(String... addresses) { request.addCc(addresses); return this; }

        /**
         * 添加密送地址（可一次添加多个）。
         *
         * @param addresses 密送邮箱地址数组
         * @return 当前构建器实例（链式调用）
         */
        public MailRequestBuilder bcc(String... addresses) { request.addBcc(addresses); return this; }

        // ---- 主题与内容 ----

        /**
         * 设置邮件主题。
         *
         * @param subject 邮件主题
         * @return 当前构建器实例（链式调用）
         */
        public MailRequestBuilder subject(String subject) { request.setSubject(subject); return this; }

        /**
         * 设置纯文本正文内容。
         *
         * <p>调用此方法后，邮件内容类型将被设为 {@code text/plain}。</p>
         *
         * @param content 纯文本内容
         * @return 当前构建器实例（链式调用）
         */
        public MailRequestBuilder text(String content) { request.setContent(content); request.setHtml(false); return this; }

        /**
         * 设置 HTML 格式正文内容。
         *
         * <p>调用此方法后，邮件内容类型将被设为 {@code text/html}。</p>
         *
         * @param content HTML 格式内容
         * @return 当前构建器实例（链式调用）
         */
        public MailRequestBuilder html(String content) { request.setContent(content); request.setHtml(true); return this; }

        // ---- 模板与变量 ----

        /**
         * 添加模板变量，用于与模板引擎配合实现动态邮件内容。
         *
         * @param key   变量名
         * @param value 变量值
         * @return 当前构建器实例（链式调用）
         */
        public MailRequestBuilder variable(String key, Object value) { request.addVariable(key, value); return this; }

        /**
         * 设置邮件模板名称，用于与模板引擎配合渲染邮件内容。
         *
         * @param templateName 模板名称
         * @return 当前构建器实例（链式调用）
         */
        public MailRequestBuilder template(String templateName) { request.setTemplateName(templateName); return this; }

        // ---- 附件 ----

        /**
         * 添加文件附件。
         *
         * @param name 附件显示名称（如 {@code "report.pdf"}）
         * @param file 附件文件
         * @return 当前构建器实例（链式调用）
         */
        public MailRequestBuilder attachment(String name, File file) { request.addAttachment(name, file); return this; }

        // ---- 发送方法 ----

        /**
         * 构建邮件请求并同步发送，阻塞等待发送结果。
         *
         * @return 邮件响应，包含成功状态及消息 ID
         */
        public MailResponse send() { return template.send(request); }

        /**
         * 构建邮件请求并异步发送，不阻塞调用线程。
         *
         * @return 包含 {@link MailResponse} 的异步结果，可通过 {@code thenAccept} 等方式处理结果
         */
        public CompletableFuture<MailResponse> sendAsync() { return template.sendAsync(request); }
    }
}

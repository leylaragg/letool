package com.github.leyland.letool.mail.core;

import com.github.leyland.letool.mail.exception.MailException;
import com.github.leyland.letool.mail.model.MailRequest;
import com.github.leyland.letool.mail.model.MailResponse;

import java.io.File;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 面向调用方的邮件构建和发送门面。
 *
 * <p>门面保留简单的链式构建、同步发送和异步发送能力。每次提交都会先调用
 * {@link MailRequest#snapshot()} 创建已校验的不可变快照，再交给可替换的
 * {@link MailSender}，因此 Builder 和直接传入的请求可以在提交后继续安全复用。</p>
 *
 * <p>实例持有独立固定线程池。Spring 会在 Bean 销毁时调用 {@link #close()}；
 * 独立创建实例的调用方也必须主动关闭。</p>
 */
public class MailTemplate implements AutoCloseable {

    /** 两参数构造器使用的默认异步队列容量。 */
    private static final int DEFAULT_ASYNC_QUEUE_CAPACITY = 1000;

    /** 底层可替换邮件发送器。 */
    private final MailSender mailSender;

    /** 异步投递专用执行器。 */
    private final ExecutorService asyncExecutor;

    /**
     * 创建邮件门面。
     *
     * @param mailSender 底层发送器，不允许为 {@code null}
     * @param asyncPoolSize 异步线程数，必须大于 0
     * @throws IllegalArgumentException 当参数不合法时抛出
     */
    public MailTemplate(MailSender mailSender, int asyncPoolSize) {
        this(mailSender, asyncPoolSize, DEFAULT_ASYNC_QUEUE_CAPACITY);
    }

    /**
     * 创建使用有界异步队列的邮件门面。
     *
     * @param mailSender 底层发送器，不允许为 {@code null}
     * @param asyncPoolSize 异步线程数，必须大于 0
     * @param asyncQueueCapacity 等待执行的最大异步任务数，必须大于 0
     * @throws IllegalArgumentException 当参数不合法时抛出
     */
    public MailTemplate(
            MailSender mailSender,
            int asyncPoolSize,
            int asyncQueueCapacity) {
        if (mailSender == null) {
            throw new IllegalArgumentException("mailSender must not be null");
        }
        if (asyncPoolSize <= 0) {
            throw new IllegalArgumentException(
                    "asyncPoolSize must be greater than zero"
            );
        }
        if (asyncQueueCapacity <= 0) {
            throw new IllegalArgumentException(
                    "asyncQueueCapacity must be greater than zero"
            );
        }
        this.mailSender = mailSender;
        this.asyncExecutor = new ThreadPoolExecutor(
                asyncPoolSize,
                asyncPoolSize,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(asyncQueueCapacity),
                mailThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    /**
     * 创建新的邮件请求构建器。
     *
     * @return 与当前门面关联的独立构建器
     */
    public MailRequestBuilder builder() {
        return new MailRequestBuilder(this);
    }

    /**
     * 同步发送邮件请求。
     *
     * @param request 待发送请求，不允许为 {@code null}
     * @return 底层发送器返回的非空响应
     * @throws MailException 当请求不合法或底层投递失败时抛出
     */
    public MailResponse send(MailRequest request) {
        return sendSnapshot(requireRequest(request).snapshot());
    }

    /**
     * 异步发送邮件请求。
     *
     * <p>请求会在当前调用线程中完成校验和快照。任务提交被拒绝时返回一个失败的
     * {@link CompletableFuture}，不会泄露执行器实现异常。</p>
     *
     * @param request 待发送请求，不允许为 {@code null}
     * @return 邮件投递异步结果
     * @throws MailException 当请求本身不合法时同步抛出
     */
    public CompletableFuture<MailResponse> sendAsync(MailRequest request) {
        MailRequest snapshot = requireRequest(request).snapshot();
        try {
            return CompletableFuture.supplyAsync(
                    () -> sendSnapshot(snapshot),
                    asyncExecutor
            );
        } catch (RejectedExecutionException exception) {
            return CompletableFuture.failedFuture(
                    MailException.asyncUnavailable(exception)
            );
        }
    }

    /**
     * 停止接收新的异步任务，已提交任务仍会继续执行。
     */
    @Override
    public void close() {
        asyncExecutor.shutdown();
    }

    /**
     * 将已冻结请求交给底层发送器并统一异常。
     *
     * @param snapshot 已校验请求快照
     * @return 非空邮件响应
     */
    private MailResponse sendSnapshot(MailRequest snapshot) {
        try {
            MailResponse response = mailSender.send(snapshot);
            if (response == null) {
                throw new IllegalStateException("mailSender returned null");
            }
            return response;
        } catch (MailException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw MailException.deliveryFailed(exception);
        }
    }

    /**
     * 校验直接传入的请求。
     *
     * @param request 待校验请求
     * @return 非空请求
     */
    private static MailRequest requireRequest(MailRequest request) {
        if (request == null) {
            throw MailException.requestInvalid("request");
        }
        return request;
    }

    /**
     * 创建带稳定名称的邮件异步线程工厂。
     *
     * @return 邮件线程工厂
     */
    private static ThreadFactory mailThreadFactory() {
        AtomicInteger sequence = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(
                    task,
                    "letool-mail-async-" + sequence.incrementAndGet()
            );
            thread.setDaemon(false);
            return thread;
        };
    }

    /**
     * 邮件请求链式构建器。
     *
     * <p>构建器本身不是线程安全对象。每个发送流程应通过
     * {@link MailTemplate#builder()} 获取独立实例。</p>
     */
    public static final class MailRequestBuilder {

        /** 最终执行发送的邮件门面。 */
        private final MailTemplate template;

        /** 当前正在构建的可变请求。 */
        private final MailRequest request = new MailRequest();

        /**
         * 创建与指定门面关联的构建器。
         *
         * @param template 邮件门面
         */
        private MailRequestBuilder(MailTemplate template) {
            this.template = template;
        }

        /**
         * 选择本次投递使用的 SMTP 账户。
         *
         * @param accountName 账户配置名称
         * @return 当前构建器
         */
        public MailRequestBuilder account(String accountName) {
            request.setAccountName(accountName);
            return this;
        }

        /**
         * 设置请求级发件人地址。
         *
         * @param from 发件人地址
         * @return 当前构建器
         */
        public MailRequestBuilder from(String from) {
            request.setFrom(from);
            return this;
        }

        /**
         * 设置请求级发件人地址和显示名称。
         *
         * @param from 发件人地址
         * @param personal 显示名称
         * @return 当前构建器
         */
        public MailRequestBuilder from(String from, String personal) {
            request.setFrom(from);
            request.setPersonal(personal);
            return this;
        }

        /**
         * 添加主收件人。
         *
         * @param addresses 收件人地址
         * @return 当前构建器
         */
        public MailRequestBuilder to(String... addresses) {
            request.addTo(addresses);
            return this;
        }

        /**
         * 添加抄送地址。
         *
         * @param addresses 抄送地址
         * @return 当前构建器
         */
        public MailRequestBuilder cc(String... addresses) {
            request.addCc(addresses);
            return this;
        }

        /**
         * 添加密送地址。
         *
         * @param addresses 密送地址
         * @return 当前构建器
         */
        public MailRequestBuilder bcc(String... addresses) {
            request.addBcc(addresses);
            return this;
        }

        /**
         * 设置邮件主题。
         *
         * @param subject 邮件主题
         * @return 当前构建器
         */
        public MailRequestBuilder subject(String subject) {
            request.setSubject(subject);
            return this;
        }

        /**
         * 设置纯文本正文。
         *
         * @param content 纯文本正文
         * @return 当前构建器
         */
        public MailRequestBuilder text(String content) {
            request.setContent(content);
            request.setHtml(false);
            return this;
        }

        /**
         * 设置 HTML 正文。
         *
         * @param content HTML 正文
         * @return 当前构建器
         */
        public MailRequestBuilder html(String content) {
            request.setContent(content);
            request.setHtml(true);
            return this;
        }

        /**
         * 添加文件附件。
         *
         * @param name 附件显示名称
         * @param file 附件文件
         * @return 当前构建器
         */
        public MailRequestBuilder attachment(String name, File file) {
            request.addAttachment(name, file);
            return this;
        }

        /**
         * 构建快照并同步发送。
         *
         * @return 邮件发送响应
         */
        public MailResponse send() {
            return template.send(request);
        }

        /**
         * 构建快照并异步发送。
         *
         * @return 邮件发送异步结果
         */
        public CompletableFuture<MailResponse> sendAsync() {
            return template.sendAsync(request);
        }
    }
}

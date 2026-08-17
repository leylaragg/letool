package io.github.leylaragg.letool.mail.core;

import io.github.leylaragg.letool.mail.exception.MailException;
import io.github.leylaragg.letool.mail.model.MailRequest;
import io.github.leylaragg.letool.mail.model.MailResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link MailTemplate} 同步、异步和扩展接口集成测试。
 *
 * <p>测试使用内存 {@link MailSender}，不会访问外部 SMTP 服务。</p>
 */
@DisplayName("MailTemplate 邮件门面测试")
class MailTemplateIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("构建器应向发送器提交完整且不可修改的请求快照")
    void shouldBuildAndSendImmutableRequestSnapshot() throws Exception {
        CapturingMailSender sender = new CapturingMailSender();
        Path attachment = Files.writeString(tempDir.resolve("report.txt"), "report");

        try (MailTemplate template = new MailTemplate(sender, 1)) {
            MailResponse response = template.builder()
                    .account("marketing")
                    .from("support@example.com", "技术支持")
                    .to("user@example.com", "user@example.com")
                    .cc("audit@example.com")
                    .bcc("archive@example.com")
                    .subject("报告")
                    .html("<strong>完成</strong>")
                    .attachment("report.txt", attachment.toFile())
                    .send();

            assertThat(response.isSuccess()).isTrue();
            assertThat(sender.request.getAccountName()).isEqualTo("marketing");
            assertThat(sender.request.getFrom()).isEqualTo("support@example.com");
            assertThat(sender.request.getPersonal()).isEqualTo("技术支持");
            assertThat(sender.request.getTo()).containsExactly("user@example.com");
            assertThat(sender.request.getCc()).containsExactly("audit@example.com");
            assertThat(sender.request.getBcc()).containsExactly("archive@example.com");
            assertThat(sender.request.getSubject()).isEqualTo("报告");
            assertThat(sender.request.getContent()).isEqualTo("<strong>完成</strong>");
            assertThat(sender.request.isHtml()).isTrue();
            assertThat(sender.request.getAttachments()).hasSize(1);
            assertThatThrownBy(() -> sender.request.setSubject("不允许修改"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test
    @DisplayName("同步发送应统一包装自定义发送器异常")
    void shouldWrapSynchronousSenderFailure() {
        IllegalStateException cause =
                new IllegalStateException("recipient=user@example.com secret=password");
        try (MailTemplate template = new MailTemplate(request -> {
            throw cause;
        }, 1)) {
            assertThatThrownBy(() -> template.send(validRequest()))
                    .isInstanceOfSatisfying(MailException.class, exception -> {
                        assertThat(exception.getCode()).isEqualTo("MAIL_003");
                        assertThat(exception.getCause()).isSameAs(cause);
                        assertThat(exception.getMessage())
                                .doesNotContain("user@example.com")
                                .doesNotContain("password");
                    });
        }
    }

    @Test
    @DisplayName("已有邮件异常不应被重复包装")
    void shouldPreserveExistingMailException() {
        MailException expected = MailException.requestInvalid("custom");
        try (MailTemplate template = new MailTemplate(request -> {
            throw expected;
        }, 1)) {
            assertThatThrownBy(() -> template.send(validRequest()))
                    .isSameAs(expected);
        }
    }

    @Test
    @DisplayName("异步发送应在提交时冻结请求")
    void shouldSnapshotRequestBeforeAsyncExecution() throws Exception {
        CountDownLatch senderStarted = new CountDownLatch(1);
        CountDownLatch allowCapture = new CountDownLatch(1);
        AtomicReference<MailRequest> captured = new AtomicReference<>();
        MailSender sender = request -> {
            senderStarted.countDown();
            await(allowCapture);
            captured.set(request);
            return MailResponse.success("async-message");
        };
        MailRequest request = validRequest();

        try (MailTemplate template = new MailTemplate(sender, 1)) {
            CompletableFuture<MailResponse> future = template.sendAsync(request);
            assertThat(senderStarted.await(5, TimeUnit.SECONDS)).isTrue();
            request.setSubject("提交后的修改");
            request.addTo("other@example.com");
            allowCapture.countDown();

            assertThat(future.join().isSuccess()).isTrue();
            assertThat(captured.get().getSubject()).isEqualTo("主题");
            assertThat(captured.get().getTo()).containsExactly("user@example.com");
        }
    }

    @Test
    @DisplayName("异步发送失败应在 Future 中保留统一投递异常")
    void shouldExposeAsyncDeliveryFailure() {
        IllegalStateException cause = new IllegalStateException("smtp down");
        try (MailTemplate template = new MailTemplate(request -> {
            throw cause;
        }, 1)) {
            assertThatThrownBy(() -> template.sendAsync(validRequest()).join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(MailException.class)
                    .satisfies(throwable -> {
                        MailException exception =
                                (MailException) throwable.getCause();
                        assertThat(exception.getCode()).isEqualTo("MAIL_003");
                        assertThat(exception.getCause()).isSameAs(cause);
                    });
        }
    }

    @Test
    @DisplayName("关闭后异步提交应返回带稳定错误码的失败 Future")
    void shouldReturnFailedFutureAfterClose() {
        MailTemplate template = new MailTemplate(
                request -> MailResponse.success("message"),
                1
        );
        template.close();

        CompletableFuture<MailResponse> future =
                template.sendAsync(validRequest());

        assertThatThrownBy(future::join)
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(MailException.class)
                .satisfies(throwable -> assertThat(
                        ((MailException) throwable.getCause()).getCode()
                ).isEqualTo("MAIL_004"));
    }

    @Test
    @DisplayName("异步队列满时应返回带稳定错误码的失败 Future")
    void shouldRejectAsyncSubmissionWhenQueueIsFull() throws Exception {
        CountDownLatch senderStarted = new CountDownLatch(1);
        CountDownLatch allowSend = new CountDownLatch(1);
        MailSender sender = request -> {
            senderStarted.countDown();
            await(allowSend);
            return MailResponse.success("message");
        };

        try (MailTemplate template = new MailTemplate(sender, 1, 1)) {
            CompletableFuture<MailResponse> running =
                    template.sendAsync(validRequest());
            assertThat(senderStarted.await(5, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<MailResponse> queued =
                    template.sendAsync(validRequest());
            CompletableFuture<MailResponse> rejected =
                    template.sendAsync(validRequest());

            allowSend.countDown();
            assertThat(running.join().isSuccess()).isTrue();
            assertThat(queued.join().isSuccess()).isTrue();
            assertThatThrownBy(rejected::join)
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(MailException.class)
                    .satisfies(throwable -> assertThat(
                            ((MailException) throwable.getCause()).getCode()
                    ).isEqualTo("MAIL_004"));
        } finally {
            allowSend.countDown();
        }
    }

    @Test
    @DisplayName("构造器应拒绝空发送器和非正线程池参数")
    void shouldValidateConstructorArguments() {
        assertThatThrownBy(() -> new MailTemplate(null, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mailSender");
        assertThatThrownBy(() -> new MailTemplate(
                request -> MailResponse.success("message"),
                0
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("asyncPoolSize");
        assertThatThrownBy(() -> new MailTemplate(
                request -> MailResponse.success("message"),
                1,
                0
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("asyncQueueCapacity");
    }

    /**
     * 创建满足最小发送要求的请求。
     *
     * @return 有效请求
     */
    private static MailRequest validRequest() {
        MailRequest request = new MailRequest();
        request.addTo("user@example.com");
        request.setSubject("主题");
        request.setContent("正文");
        return request;
    }

    /**
     * 等待测试闩锁并恢复中断标记。
     *
     * @param latch 待等待闩锁
     */
    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("等待测试闩锁超时");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待测试闩锁被中断", exception);
        }
    }

    /**
     * 捕获请求并返回成功结果的内存发送器。
     */
    private static class CapturingMailSender implements MailSender {

        /** 最近一次收到的不可变请求。 */
        private MailRequest request;

        /**
         * 捕获请求。
         *
         * @param request 邮件请求
         * @return 固定成功响应
         */
        @Override
        public MailResponse send(MailRequest request) {
            this.request = request;
            return MailResponse.success("captured-message");
        }
    }
}

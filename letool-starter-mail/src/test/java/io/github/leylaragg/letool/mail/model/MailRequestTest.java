package io.github.leylaragg.letool.mail.model;

import io.github.leylaragg.letool.mail.exception.MailException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link MailRequest} 构建、校验和快照测试。
 */
@DisplayName("MailRequest 请求模型测试")
class MailRequestTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("应保存账户、收件人、正文和附件")
    void shouldStoreMailFields() throws Exception {
        Path attachment = Files.writeString(tempDir.resolve("报告.txt"), "content");
        MailRequest request = validRequest();
        request.setAccountName("marketing");
        request.setFrom("support@example.com");
        request.setPersonal("技术支持");
        request.addCc("audit@example.com");
        request.addBcc("archive@example.com");
        request.setHtml(true);
        request.addAttachment("报告.txt", attachment.toFile());

        assertThat(request.getAccountName()).isEqualTo("marketing");
        assertThat(request.getFrom()).isEqualTo("support@example.com");
        assertThat(request.getPersonal()).isEqualTo("技术支持");
        assertThat(request.getTo()).containsExactly("user@example.com");
        assertThat(request.getCc()).containsExactly("audit@example.com");
        assertThat(request.getBcc()).containsExactly("archive@example.com");
        assertThat(request.isHtml()).isTrue();
        assertThat(request.getAttachments()).singleElement().satisfies(value -> {
            assertThat(value.getName()).isEqualTo("报告.txt");
            assertThat(value.getFile()).isEqualTo(attachment.toFile());
        });
    }

    @Test
    @DisplayName("收件人应去重并保持插入顺序")
    void shouldDeduplicateRecipientsInInsertionOrder() {
        MailRequest request = validRequest();

        request.addTo(
                "second@example.com",
                "user@example.com",
                "third@example.com"
        );

        assertThat(request.getTo()).containsExactly(
                "user@example.com",
                "second@example.com",
                "third@example.com"
        );
    }

    @Test
    @DisplayName("集合访问器不应允许绕过添加方法修改内部状态")
    void shouldExposeUnmodifiableCollections() {
        MailRequest request = validRequest();

        assertThatThrownBy(() -> request.getTo().add("other@example.com"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> request.getAttachments().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("快照应与原请求后续修改隔离且自身不可修改")
    void shouldCreateImmutableSnapshot() {
        MailRequest request = validRequest();

        MailRequest snapshot = request.snapshot();
        request.addTo("other@example.com");
        request.setSubject("修改后的主题");

        assertThat(snapshot.getTo()).containsExactly("user@example.com");
        assertThat(snapshot.getSubject()).isEqualTo("主题");
        assertThatThrownBy(() -> snapshot.setSubject("不允许修改"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(snapshot.snapshot()).isSameAs(snapshot);
    }

    @Test
    @DisplayName("无效邮箱地址应转换为请求错误")
    void shouldRejectInvalidRecipientAddress() {
        MailRequest request = new MailRequest();

        assertThatThrownBy(() -> request.addTo("not-an-email"))
                .isInstanceOfSatisfying(MailException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("MAIL_002");
                    assertThat(exception.getMessage()).contains("to");
                    assertThat(exception.getMessage()).doesNotContain("not-an-email");
                });
    }

    @Test
    @DisplayName("快照必须至少包含一个收件人")
    void shouldRequireAtLeastOneRecipient() {
        MailRequest request = new MailRequest();
        request.setSubject("主题");
        request.setContent("正文");

        assertRequestInvalid(request, "recipients");
    }

    @Test
    @DisplayName("快照必须包含主题和正文")
    void shouldRequireSubjectAndContent() {
        MailRequest missingSubject = new MailRequest();
        missingSubject.addTo("user@example.com");
        missingSubject.setContent("正文");
        assertRequestInvalid(missingSubject, "subject");

        MailRequest missingContent = new MailRequest();
        missingContent.addTo("user@example.com");
        missingContent.setSubject("主题");
        assertRequestInvalid(missingContent, "content");
    }

    @Test
    @DisplayName("快照应拒绝不存在或不可读的附件")
    void shouldRejectUnreadableAttachment() {
        MailRequest request = validRequest();
        request.addAttachment(
                "missing.txt",
                tempDir.resolve("missing.txt").toFile()
        );

        assertRequestInvalid(request, "attachments");
    }

    /**
     * 创建满足最小发送要求的请求。
     *
     * @return 有效邮件请求
     */
    private static MailRequest validRequest() {
        MailRequest request = new MailRequest();
        request.addTo("user@example.com");
        request.setSubject("主题");
        request.setContent("正文");
        return request;
    }

    /**
     * 断言请求快照会产生稳定请求错误。
     *
     * @param request 待校验请求
     * @param field 预期安全字段名
     */
    private static void assertRequestInvalid(MailRequest request, String field) {
        assertThatThrownBy(request::snapshot)
                .isInstanceOfSatisfying(MailException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("MAIL_002");
                    assertThat(exception.getMessage()).contains(field);
                });
    }
}

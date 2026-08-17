package io.github.leylaragg.letool.mail.core;

import io.github.leylaragg.letool.mail.config.MailProperties;
import io.github.leylaragg.letool.mail.exception.MailException;
import io.github.leylaragg.letool.mail.model.MailRequest;
import io.github.leylaragg.letool.mail.model.MailResponse;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimeUtility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link DefaultMailSender} MIME 构造和传输边界测试。
 *
 * <p>测试替换最后一步网络传输，不访问外部 SMTP 服务；消息构造和 Jakarta Mail
 * 会话属性仍使用真实实现。</p>
 */
@DisplayName("DefaultMailSender 默认发送器测试")
class DefaultMailSenderTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("应使用请求选择的账户构造 HTML 附件邮件")
    void shouldBuildHtmlMessageWithSelectedAccount() throws Exception {
        MailProperties properties = mailProperties();
        AtomicReference<Session> capturedSession = new AtomicReference<>();
        AtomicReference<MailProperties.AccountConfig> capturedAccount =
                new AtomicReference<>();
        AtomicReference<MimeMessage> capturedMessage = new AtomicReference<>();
        DefaultMailSender sender = new DefaultMailSender(
                properties,
                (session, account, message) -> {
                    capturedSession.set(session);
                    capturedAccount.set(account);
                    capturedMessage.set(message);
                }
        );
        Path attachment = Files.writeString(tempDir.resolve("报告.txt"), "report");
        MailRequest request = validRequest();
        request.setAccountName("marketing");
        request.setFrom("support@example.com");
        request.setPersonal("技术支持");
        request.addCc("audit@example.com");
        request.addBcc("archive@example.com");
        request.setContent("<strong>完成</strong>");
        request.setHtml(true);
        request.addAttachment("报告.txt", attachment.toFile());

        MailResponse response = sender.send(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessageId()).isNotBlank();
        assertThat(capturedAccount.get())
                .isSameAs(properties.getAccounts().get("marketing"));
        Session session = capturedSession.get();
        assertThat(session.getProperty("mail.transport.protocol")).isEqualTo("smtp");
        assertThat(session.getProperty("mail.smtp.host"))
                .isEqualTo("smtp.marketing.example");
        assertThat(session.getProperty("mail.smtp.connectiontimeout"))
                .isEqualTo("1100");
        assertThat(session.getProperty("mail.smtp.timeout")).isEqualTo("2200");
        assertThat(session.getProperty("mail.smtp.writetimeout")).isEqualTo("3300");
        assertThat(session.getProperty("mail.smtp.starttls.enable")).isEqualTo("true");

        MimeMessage message = capturedMessage.get();
        assertThat(((InternetAddress) message.getFrom()[0]).getAddress())
                .isEqualTo("support@example.com");
        assertThat(((InternetAddress) message.getFrom()[0]).getPersonal())
                .isEqualTo("技术支持");
        assertThat(message.getRecipients(Message.RecipientType.TO))
                .extracting(Object::toString)
                .containsExactly("user@example.com");
        assertThat(message.getRecipients(Message.RecipientType.CC))
                .extracting(Object::toString)
                .containsExactly("audit@example.com");
        assertThat(message.getRecipients(Message.RecipientType.BCC))
                .extracting(Object::toString)
                .containsExactly("archive@example.com");
        assertThat(message.getSubject()).isEqualTo("主题");
        assertThat(message.getContent()).isInstanceOf(MimeMultipart.class);
        MimeMultipart multipart = (MimeMultipart) message.getContent();
        assertThat(multipart.getBodyPart(0).getContent())
                .isEqualTo("<strong>完成</strong>");
        assertThat(MimeUtility.decodeText(
                multipart.getBodyPart(1).getFileName()
        )).isEqualTo("报告.txt");
    }

    @Test
    @DisplayName("无附件纯文本邮件不应强制包装为 multipart")
    void shouldBuildPlainTextMessageWithoutMultipart() throws Exception {
        AtomicReference<MimeMessage> capturedMessage = new AtomicReference<>();
        DefaultMailSender sender = new DefaultMailSender(
                mailProperties(),
                (session, account, message) -> capturedMessage.set(message)
        );

        sender.send(validRequest());

        assertThat(capturedMessage.get().getContent()).isEqualTo("正文");
        assertThat(capturedMessage.get().getContentType())
                .startsWith("text/plain");
    }

    @Test
    @DisplayName("底层传输失败应转换为安全的统一异常")
    void shouldWrapTransportFailureWithoutLeakingDetails() {
        MessagingException cause =
                new MessagingException("user@example.com password=secret");
        DefaultMailSender sender = new DefaultMailSender(
                mailProperties(),
                (session, account, message) -> {
                    throw cause;
                }
        );

        assertThatThrownBy(() -> sender.send(validRequest()))
                .isInstanceOfSatisfying(MailException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("MAIL_003");
                    assertThat(exception.getCause()).isSameAs(cause);
                    assertThat(exception.getMessage())
                            .doesNotContain("user@example.com")
                            .doesNotContain("secret");
                });
    }

    /**
     * 创建包含默认账户和营销账户的有效配置。
     *
     * @return 邮件模块配置
     */
    private static MailProperties mailProperties() {
        MailProperties properties = new MailProperties();
        properties.setDefaultAccount("primary");
        HashMap<String, MailProperties.AccountConfig> accounts = new HashMap<>();
        accounts.put("primary", account("smtp.primary.example"));
        MailProperties.AccountConfig marketing =
                account("smtp.marketing.example");
        marketing.setConnectionTimeoutMillis(1100);
        marketing.setReadTimeoutMillis(2200);
        marketing.setWriteTimeoutMillis(3300);
        accounts.put("marketing", marketing);
        properties.setAccounts(accounts);
        return properties;
    }

    /**
     * 创建有效 SMTP 账户。
     *
     * @param host SMTP 服务器
     * @return 账户配置
     */
    private static MailProperties.AccountConfig account(String host) {
        MailProperties.AccountConfig account = new MailProperties.AccountConfig();
        account.setHost(host);
        account.setPort(587);
        account.setUsername("mailer@example.com");
        account.setPassword("password");
        account.setProtocol("smtp");
        account.setAuth(true);
        account.setStarttls(true);
        account.setFrom("mailer@example.com");
        account.setPersonal("系统通知");
        return account;
    }

    /**
     * 创建满足最小发送要求的邮件请求。
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
}

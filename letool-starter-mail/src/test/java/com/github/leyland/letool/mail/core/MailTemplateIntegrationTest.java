package com.github.leyland.letool.mail.core;

import com.github.leyland.letool.mail.exception.MailException;
import com.github.leyland.letool.mail.model.MailRequest;
import com.github.leyland.letool.mail.model.MailResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Local integration tests for {@link MailTemplate}.
 *
 * <p>The tests use in-memory {@link MailSender} implementations so no SMTP server is required.</p>
 */
class MailTemplateIntegrationTest {

    @TempDir
    Path tempDir;

    /**
     * Verifies that the builder populates a complete request before delegating to the sender.
     */
    @Test
    void shouldBuildAndSendMailRequest() throws Exception {
        CapturingMailSender sender = new CapturingMailSender();
        MailTemplate template = new MailTemplate(sender, 1);
        Path attachment = Files.writeString(tempDir.resolve("report.txt"), "report");

        MailResponse response = template.builder()
                .from("support@example.com", "Support")
                .to("user@example.com", "user@example.com")
                .cc("audit@example.com")
                .bcc("archive@example.com")
                .subject("Report")
                .html("<strong>Ready</strong>")
                .variable("month", "2026-07")
                .template("monthly-report")
                .attachment("report.txt", attachment.toFile())
                .send();
        template.close();

        assertThat(response.isSuccess()).isTrue();
        assertThat(sender.request.getFrom()).isEqualTo("support@example.com");
        assertThat(sender.request.getPersonal()).isEqualTo("Support");
        assertThat(sender.request.getTo()).containsExactly("user@example.com");
        assertThat(sender.request.getCc()).containsExactly("audit@example.com");
        assertThat(sender.request.getBcc()).containsExactly("archive@example.com");
        assertThat(sender.request.getSubject()).isEqualTo("Report");
        assertThat(sender.request.getContent()).isEqualTo("<strong>Ready</strong>");
        assertThat(sender.request.isHtml()).isTrue();
        assertThat(sender.request.getVariables()).containsEntry("month", "2026-07");
        assertThat(sender.request.getTemplateName()).isEqualTo("monthly-report");
        assertThat(sender.request.getAttachments()).singleElement().satisfies(att -> {
            assertThat(att.getName()).isEqualTo("report.txt");
            assertThat(att.getFile()).isEqualTo(attachment.toFile());
        });
    }

    /**
     * Verifies async failures are exposed as {@link MailException}.
     */
    @Test
    void shouldWrapAsyncSendFailure() {
        MailTemplate template = new MailTemplate(request -> {
            throw new IllegalStateException("smtp down");
        }, 1);
        MailRequest request = new MailRequest();
        request.addTo("user@example.com");
        request.setSubject("Async");
        request.setContent("Body");

        assertThatThrownBy(() -> template.sendAsync(request).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(MailException.class)
                .hasMessageContaining("Async mail send failed");
        template.close();
    }

    /**
     * Test sender that captures the request and returns a successful response.
     */
    static class CapturingMailSender implements MailSender {

        private MailRequest request;

        @Override
        public MailResponse send(MailRequest request) {
            this.request = request;
            return MailResponse.success("captured-message");
        }
    }
}

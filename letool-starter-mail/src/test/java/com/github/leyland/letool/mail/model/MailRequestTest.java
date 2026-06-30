package com.github.leyland.letool.mail.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MailRequest 模型单元测试。
 *
 * @author leyland
 */
@DisplayName("MailRequest 邮件请求模型测试")
class MailRequestTest {

    // ======================== 基本字段 Getter / Setter ========================

    @Nested
    @DisplayName("基本字段 Getter / Setter 测试")
    class BasicFieldTests {

        @Test
        @DisplayName("应正确设置和获取 from 字段")
        void shouldSetAndGetFrom() {
            MailRequest request = new MailRequest();
            request.setFrom("sender@example.com");
            assertEquals("sender@example.com", request.getFrom());
        }

        @Test
        @DisplayName("应正确设置和获取 personal 字段")
        void shouldSetAndGetPersonal() {
            MailRequest request = new MailRequest();
            request.setPersonal("系统通知");
            assertEquals("系统通知", request.getPersonal());
        }

        @Test
        @DisplayName("应正确设置和获取 subject 字段")
        void shouldSetAndGetSubject() {
            MailRequest request = new MailRequest();
            request.setSubject("会议提醒");
            assertEquals("会议提醒", request.getSubject());
        }

        @Test
        @DisplayName("应正确设置和获取 content 字段")
        void shouldSetAndGetContent() {
            MailRequest request = new MailRequest();
            request.setContent("这是一封测试邮件");
            assertEquals("这是一封测试邮件", request.getContent());
        }

        @Test
        @DisplayName("应正确设置和获取 html 标志")
        void shouldSetAndGetHtmlFlag() {
            MailRequest request = new MailRequest();
            assertFalse(request.isHtml(), "默认应为纯文本模式");

            request.setHtml(true);
            assertTrue(request.isHtml(), "设置为 HTML 后应返回 true");
        }

        @Test
        @DisplayName("应正确设置和获取 templateName 字段")
        void shouldSetAndGetTemplateName() {
            MailRequest request = new MailRequest();
            request.setTemplateName("welcome-email");
            assertEquals("welcome-email", request.getTemplateName());
        }

        @Test
        @DisplayName("新创建的 MailRequest 各字段应有合理默认值")
        void shouldHaveReasonableDefaults() {
            MailRequest request = new MailRequest();
            assertNull(request.getFrom());
            assertNull(request.getPersonal());
            assertNull(request.getSubject());
            assertNull(request.getContent());
            assertNull(request.getTemplateName());
            assertFalse(request.isHtml());
            assertNotNull(request.getTo());
            assertNotNull(request.getCc());
            assertNotNull(request.getBcc());
            assertNotNull(request.getVariables());
            assertNotNull(request.getAttachments());
            assertTrue(request.getTo().isEmpty());
            assertTrue(request.getCc().isEmpty());
            assertTrue(request.getBcc().isEmpty());
            assertTrue(request.getVariables().isEmpty());
            assertTrue(request.getAttachments().isEmpty());
        }
    }

    // ======================== 收件人地址管理 ========================

    @Nested
    @DisplayName("收件人地址管理测试")
    class RecipientManagementTests {

        @Test
        @DisplayName("addTo 应正确添加收件人地址")
        void shouldAddToRecipients() {
            MailRequest request = new MailRequest();
            request.addTo("user1@example.com", "user2@example.com");
            Set<String> to = request.getTo();
            assertEquals(2, to.size());
            assertTrue(to.contains("user1@example.com"));
            assertTrue(to.contains("user2@example.com"));
        }

        @Test
        @DisplayName("addCc 应正确添加抄送地址")
        void shouldAddCcRecipients() {
            MailRequest request = new MailRequest();
            request.addCc("cc1@example.com", "cc2@example.com");
            Set<String> cc = request.getCc();
            assertEquals(2, cc.size());
            assertTrue(cc.contains("cc1@example.com"));
            assertTrue(cc.contains("cc2@example.com"));
        }

        @Test
        @DisplayName("addBcc 应正确添加密送地址")
        void shouldAddBccRecipients() {
            MailRequest request = new MailRequest();
            request.addBcc("bcc1@example.com", "bcc2@example.com");
            Set<String> bcc = request.getBcc();
            assertEquals(2, bcc.size());
            assertTrue(bcc.contains("bcc1@example.com"));
            assertTrue(bcc.contains("bcc2@example.com"));
        }

        @Test
        @DisplayName("重复添加同一地址应自动去重")
        void shouldDeduplicateAddresses() {
            MailRequest request = new MailRequest();
            request.addTo("user@example.com");
            request.addTo("user@example.com");
            assertEquals(1, request.getTo().size());
        }

        @Test
        @DisplayName("收件人地址应保持插入顺序")
        void shouldPreserveInsertionOrder() {
            MailRequest request = new MailRequest();
            request.addTo("c@example.com", "a@example.com", "b@example.com");

            String[] addresses = request.getTo().toArray(new String[0]);
            assertEquals("c@example.com", addresses[0]);
            assertEquals("a@example.com", addresses[1]);
            assertEquals("b@example.com", addresses[2]);
        }
    }

    // ======================== 模板变量 ========================

    @Nested
    @DisplayName("模板变量管理测试")
    class VariableManagementTests {

        @Test
        @DisplayName("addVariable 应正确添加模板变量")
        void shouldAddVariables() {
            MailRequest request = new MailRequest();
            request.addVariable("username", "张三");
            request.addVariable("code", 123456);

            Map<String, Object> vars = request.getVariables();
            assertEquals(2, vars.size());
            assertEquals("张三", vars.get("username"));
            assertEquals(123456, vars.get("code"));
        }

        @Test
        @DisplayName("同名变量重复添加应覆盖旧值")
        void shouldOverwriteDuplicateVariable() {
            MailRequest request = new MailRequest();
            request.addVariable("key", "value1");
            request.addVariable("key", "value2");
            assertEquals(1, request.getVariables().size());
            assertEquals("value2", request.getVariables().get("key"));
        }
    }

    // ======================== 附件管理 ========================

    @Nested
    @DisplayName("附件管理测试")
    class AttachmentManagementTests {

        @Test
        @DisplayName("addAttachment 应正确添加附件")
        void shouldAddAttachments() {
            MailRequest request = new MailRequest();
            File file1 = new File("/path/to/report.pdf");
            File file2 = new File("/path/to/image.png");
            request.addAttachment("月报.pdf", file1);
            request.addAttachment("截图.png", file2);

            List<MailRequest.Attachment> attachments = request.getAttachments();
            assertEquals(2, attachments.size());
            assertEquals("月报.pdf", attachments.get(0).getName());
            assertEquals(file1, attachments.get(0).getFile());
            assertEquals("截图.png", attachments.get(1).getName());
            assertEquals(file2, attachments.get(1).getFile());
        }

        @Test
        @DisplayName("附件内部类 Attachment 应正确存储名称和文件")
        void attachmentShouldStoreNameAndFile() {
            File file = new File("/tmp/test.txt");
            MailRequest.Attachment attachment = new MailRequest.Attachment("测试文件.txt", file);
            assertEquals("测试文件.txt", attachment.getName());
            assertEquals(file, attachment.getFile());
        }
    }

    // ======================== 综合场景 ========================

    @Nested
    @DisplayName("综合场景测试")
    class IntegrationScenarioTests {

        @Test
        @DisplayName("完整构建一封邮件应包含所有字段")
        void shouldBuildCompleteEmail() {
            MailRequest request = new MailRequest();
            request.setFrom("noreply@company.com");
            request.setPersonal("系统通知");
            request.addTo("user@example.com");
            request.addCc("admin@example.com");
            request.setSubject("您的订单已发货");
            request.setContent("<h1>订单状态</h1><p>您的订单 #12345 已发货。</p>");
            request.setHtml(true);
            request.addVariable("orderId", "12345");
            request.addVariable("userName", "李四");
            request.addAttachment("invoice.pdf", new File("/tmp/invoice.pdf"));

            assertEquals("noreply@company.com", request.getFrom());
            assertEquals("系统通知", request.getPersonal());
            assertEquals(1, request.getTo().size());
            assertTrue(request.getTo().contains("user@example.com"));
            assertEquals(1, request.getCc().size());
            assertTrue(request.getCc().contains("admin@example.com"));
            assertEquals("您的订单已发货", request.getSubject());
            assertTrue(request.getContent().contains("订单状态"));
            assertTrue(request.isHtml());
            assertEquals(2, request.getVariables().size());
            assertEquals(1, request.getAttachments().size());
        }
    }
}

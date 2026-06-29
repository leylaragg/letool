package com.github.leyland.letool.mail.core;

import com.github.leyland.letool.mail.config.MailProperties;
import com.github.leyland.letool.mail.exception.MailException;
import com.github.leyland.letool.mail.model.MailRequest;
import com.github.leyland.letool.mail.model.MailResponse;
import jakarta.activation.DataHandler;
import jakarta.activation.FileDataSource;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Properties;

// ======================== 类级别说明 ========================

/**
 * <p>{@link MailSender} 的 Jakarta Mail 实现 — 邮件模块的默认发送引擎。</p>
 *
 * <h3>核心职责</h3>
 * <ul>
 *   <li>基于 Jakarta Mail ({@link jakarta.mail.Session}) 构建并发送 MIME 格式邮件。</li>
 *   <li>支持纯文本 ({@code text/plain}) 与 HTML ({@code text/html}) 内容格式。</li>
 *   <li>支持文件附件，自动处理文件名编码。</li>
 *   <li>支持设置发件人 ({@code From})、收件人 ({@code To})、抄送 ({@code Cc})、密送 ({@code Bcc})。</li>
 *   <li>支持 SSL / STARTTLS 加密连接。</li>
 *   <li>发送成功后返回 {@link MailResponse#success(String)}，失败则抛出 {@link MailException}。</li>
 * </ul>
 *
 * <h3>生命周期</h3>
 * <p>实例在构造时创建并持有 {@link Session}，设计为单例 Bean 使用（线程安全的）。</p>
 *
 * <h3>发件人地址优先级</h3>
 * <ol>
 *   <li>请求级别：{@code MailRequest.from}（可通过 {@link MailTemplate.MailRequestBuilder#from(String)} 设置）。</li>
 *   <li>配置级别：{@code letool.mail.accounts.<name>.from}（application.yml 中的账户配置）。</li>
 * </ol>
 *
 * <h3>日志策略</h3>
 * <ul>
 *   <li>发送成功：INFO 级别，记录主题、收件人列表、消息 ID。</li>
 *   <li>发送失败：ERROR 级别，记录异常详情并重新抛出 {@link MailException}。</li>
 * </ul>
 *
 * @author leyland
 * @since 1.0.0
 */
public class DefaultMailSender implements MailSender {

    // ======================== 日志与成员变量 ========================

    private static final Logger log = LoggerFactory.getLogger(DefaultMailSender.class);

    /** 邮件配置属性 */
    private final MailProperties properties;

    /** Jakarta Mail 会话实例，基于活跃账户配置创建 */
    private final Session session;

    // ======================== 构造方法 ========================

    /**
     * 构造邮件发送器。
     *
     * @param properties 邮件配置属性，从中提取活跃账户信息创建 {@link Session}
     */
    public DefaultMailSender(MailProperties properties) {
        this.properties = properties;
        this.session = createSession(properties.getActiveAccount());
    }

    // ======================== Session 创建 ========================

    /**
     * 根据账户配置创建 Jakarta Mail {@link Session}。
     *
     * <p>自动设置 host、port、auth、SSL、STARTTLS 等 SMTP 属性。</p>
     *
     * @param account 活跃账户配置
     * @return 配置好的 Jakarta Mail 会话实例
     */
    private Session createSession(MailProperties.AccountConfig account) {
        Properties props = new Properties();
        props.put("mail.smtp.host", account.getHost());
        props.put("mail.smtp.port", String.valueOf(account.getPort()));
        props.put("mail.smtp.auth", String.valueOf(account.isAuth()));
        if (account.isSsl()) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.port", String.valueOf(account.getPort()));
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        }
        if (account.isStarttls()) {
            props.put("mail.smtp.starttls.enable", "true");
        }
        return Session.getInstance(props);
    }

    // ======================== 邮件发送核心逻辑 ========================

    /**
     * 发送邮件。
     *
     * <p>完整执行以下流程：</p>
     * <ol>
     *   <li>创建 {@link MimeMessage} 实例。</li>
     *   <li>设置发件人（优先使用请求级别，回退到配置级别）。</li>
     *   <li>设置收件人、抄送、密送地址。</li>
     *   <li>设置邮件主题（UTF-8 编码）。</li>
     *   <li>构建 MIME 多段体：正文 + 附件列表。</li>
     *   <li>通过 SMTP 传输连接发送，完成后关闭连接。</li>
     *   <li>返回 {@link MailResponse} 携带消息 ID。</li>
     * </ol>
     *
     * @param request 邮件请求，包含收件人、主题、内容、附件等完整信息
     * @return 邮件响应，包含成功状态及消息 ID
     * @throws MailException 当邮件发送过程中发生任何异常时抛出
     */
    @Override
    public MailResponse send(MailRequest request) {
        try {
            // ---- 获取账户配置 ----
            MailProperties.AccountConfig account = properties.getActiveAccount();
            MimeMessage message = new MimeMessage(session);

            // ---- 设置发件人 ----
            String from = request.getFrom() != null ? request.getFrom() : account.getFrom();
            String personal = request.getPersonal() != null ? request.getPersonal() : account.getPersonal();
            if (personal != null) {
                message.setFrom(new InternetAddress(from, personal, "UTF-8"));
            } else {
                message.setFrom(new InternetAddress(from));
            }

            // ---- 设置收件人 / 抄送 / 密送 ----
            for (String addr : request.getTo()) {
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(addr));
            }
            for (String addr : request.getCc()) {
                message.addRecipient(Message.RecipientType.CC, new InternetAddress(addr));
            }
            for (String addr : request.getBcc()) {
                message.addRecipient(Message.RecipientType.BCC, new InternetAddress(addr));
            }

            // ---- 设置主题与发送时间 ----
            message.setSubject(request.getSubject(), "UTF-8");
            message.setSentDate(new Date());

            // ---- 构建 MIME 多段体 ----
            MimeMultipart multipart = new MimeMultipart();

            // 正文部分
            MimeBodyPart contentPart = new MimeBodyPart();
            if (request.isHtml()) {
                contentPart.setContent(request.getContent(), "text/html; charset=UTF-8");
            } else {
                contentPart.setText(request.getContent(), "UTF-8");
            }
            multipart.addBodyPart(contentPart);

            // 附件部分
            for (MailRequest.Attachment att : request.getAttachments()) {
                MimeBodyPart attPart = new MimeBodyPart();
                FileDataSource fds = new FileDataSource(att.getFile());
                attPart.setDataHandler(new DataHandler(fds));
                attPart.setFileName(MimeUtility.encodeText(att.getName(), "UTF-8", "B"));
                multipart.addBodyPart(attPart);
            }

            message.setContent(multipart);
            message.saveChanges();

            // ---- SMTP 传输 ----
            String username = account.getUsername() != null ? account.getUsername() : account.getFrom();
            String password = account.getPassword();
            Transport transport = session.getTransport(account.getProtocol());
            transport.connect(account.getHost(), account.getPort(), username, password);
            transport.sendMessage(message, message.getAllRecipients());
            transport.close();

            // ---- 记录日志并返回成功响应 ----
            String messageId = message.getMessageID();
            log.info("Mail sent: subject={}, to={}, messageId={}", request.getSubject(), request.getTo(), messageId);
            return MailResponse.success(messageId);
        } catch (Exception e) {
            log.error("Failed to send mail: {}", e.getMessage(), e);
            throw new MailException("Failed to send mail: " + e.getMessage(), e);
        }
    }
}

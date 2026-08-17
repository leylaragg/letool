package io.github.leylaragg.letool.mail.core;

import io.github.leylaragg.letool.mail.config.MailProperties;
import io.github.leylaragg.letool.mail.exception.MailException;
import io.github.leylaragg.letool.mail.model.MailRequest;
import io.github.leylaragg.letool.mail.model.MailResponse;
import jakarta.activation.DataHandler;
import jakarta.activation.FileDataSource;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimeUtility;

import java.util.Date;
import java.util.Locale;
import java.util.Properties;

/**
 * 基于 Jakarta Mail 的默认 SMTP 发送器。
 *
 * <p>发送器根据每个请求选择账户，创建带协议专属连接参数和有限超时的
 * {@link Session}，构造标准 MIME 消息后通过 SMTP 投递。用户需要完全替换
 * 投递逻辑时，应实现 {@link MailSender}，而不是继承本类。</p>
 */
public class DefaultMailSender implements MailSender {

    /** 邮件模块账户配置。 */
    private final MailProperties properties;

    /** 最后一步网络传输边界。 */
    private final TransportExecutor transportExecutor;

    /**
     * 创建使用真实 Jakarta Mail 传输的默认发送器。
     *
     * @param properties 邮件配置，不允许为 {@code null}
     * @throws IllegalArgumentException 当配置对象为空时抛出
     */
    public DefaultMailSender(MailProperties properties) {
        this(properties, DefaultMailSender::deliver);
    }

    /**
     * 创建可替换最后一步传输的发送器。
     *
     * <p>该构造器仅供同包契约测试使用，避免测试访问外部 SMTP 服务。</p>
     *
     * @param properties 邮件配置
     * @param transportExecutor 传输执行器
     */
    DefaultMailSender(
            MailProperties properties,
            TransportExecutor transportExecutor) {
        if (properties == null) {
            throw new IllegalArgumentException("properties must not be null");
        }
        if (transportExecutor == null) {
            throw new IllegalArgumentException(
                    "transportExecutor must not be null"
            );
        }
        this.properties = properties;
        this.transportExecutor = transportExecutor;
    }

    /**
     * 构造并投递邮件。
     *
     * @param request 邮件请求，不允许为 {@code null}
     * @return 成功响应和生成的消息标识
     * @throws MailException 当配置、请求、消息构造或投递失败时抛出
     */
    @Override
    public MailResponse send(MailRequest request) {
        if (request == null) {
            throw MailException.requestInvalid("request");
        }
        MailRequest snapshot = request.snapshot();
        MailProperties.AccountConfig account =
                properties.resolveAccount(snapshot.getAccountName());
        try {
            Session session = createSession(account);
            MimeMessage message = createMessage(session, account, snapshot);
            transportExecutor.send(session, account, message);
            return MailResponse.success(message.getMessageID());
        } catch (MailException exception) {
            throw exception;
        } catch (Exception exception) {
            throw MailException.deliveryFailed(exception);
        }
    }

    /**
     * 根据账户创建 Jakarta Mail 会话。
     *
     * @param account 已校验账户配置
     * @return 配置有限超时和 TLS 参数的会话
     */
    private static Session createSession(MailProperties.AccountConfig account) {
        String protocol = account.getProtocol().trim().toLowerCase(Locale.ROOT);
        String prefix = "mail." + protocol + ".";
        Properties sessionProperties = new Properties();
        sessionProperties.setProperty("mail.transport.protocol", protocol);
        sessionProperties.setProperty(prefix + "host", account.getHost());
        sessionProperties.setProperty(prefix + "port", String.valueOf(account.getPort()));
        sessionProperties.setProperty(prefix + "auth", String.valueOf(account.isAuth()));
        sessionProperties.setProperty(
                prefix + "connectiontimeout",
                String.valueOf(account.getConnectionTimeoutMillis())
        );
        sessionProperties.setProperty(
                prefix + "timeout",
                String.valueOf(account.getReadTimeoutMillis())
        );
        sessionProperties.setProperty(
                prefix + "writetimeout",
                String.valueOf(account.getWriteTimeoutMillis())
        );
        sessionProperties.setProperty(
                prefix + "starttls.enable",
                String.valueOf(account.isStarttls())
        );
        sessionProperties.setProperty(
                prefix + "ssl.enable",
                String.valueOf(account.isSsl() || protocol.equals("smtps"))
        );
        return Session.getInstance(sessionProperties);
    }

    /**
     * 构造完整 MIME 消息。
     *
     * @param session Jakarta Mail 会话
     * @param account 已校验账户
     * @param request 已校验请求快照
     * @return 已保存并生成消息标识的 MIME 消息
     * @throws Exception 当地址、正文或附件构造失败时抛出
     */
    private static MimeMessage createMessage(
            Session session,
            MailProperties.AccountConfig account,
            MailRequest request) throws Exception {
        MimeMessage message = new MimeMessage(session);
        setFrom(message, account, request);
        addRecipients(message, Message.RecipientType.TO, request.getTo());
        addRecipients(message, Message.RecipientType.CC, request.getCc());
        addRecipients(message, Message.RecipientType.BCC, request.getBcc());
        message.setSubject(request.getSubject(), "UTF-8");
        message.setSentDate(new Date());

        if (request.getAttachments().isEmpty()) {
            setBody(message, request);
        } else {
            message.setContent(createMultipartBody(request));
        }
        message.saveChanges();
        return message;
    }

    /**
     * 设置请求级或账户级发件人。
     *
     * @param message 待构造消息
     * @param account 账户配置
     * @param request 请求快照
     * @throws Exception 当地址或显示名称编码失败时抛出
     */
    private static void setFrom(
            MimeMessage message,
            MailProperties.AccountConfig account,
            MailRequest request) throws Exception {
        String from = request.getFrom() == null
                ? account.getFrom()
                : request.getFrom();
        String personal = request.getPersonal() == null
                ? account.getPersonal()
                : request.getPersonal();
        InternetAddress address = personal == null || personal.isBlank()
                ? new InternetAddress(from, true)
                : new InternetAddress(from, personal, "UTF-8");
        message.setFrom(address);
    }

    /**
     * 批量添加指定类型收件人。
     *
     * @param message 待构造消息
     * @param recipientType 收件人类型
     * @param addresses 邮箱地址
     * @throws MessagingException 当地址无法添加时抛出
     */
    private static void addRecipients(
            MimeMessage message,
            Message.RecipientType recipientType,
            Iterable<String> addresses) throws MessagingException {
        for (String address : addresses) {
            message.addRecipient(
                    recipientType,
                    new InternetAddress(address, true)
            );
        }
    }

    /**
     * 设置无附件消息的正文。
     *
     * @param message 待构造消息
     * @param request 请求快照
     * @throws MessagingException 当正文无法设置时抛出
     */
    private static void setBody(
            MimeMessage message,
            MailRequest request) throws MessagingException {
        if (request.isHtml()) {
            message.setContent(request.getContent(), "text/html; charset=UTF-8");
        } else {
            message.setText(request.getContent(), "UTF-8");
        }
    }

    /**
     * 创建包含正文和附件的 MIME 多段体。
     *
     * @param request 请求快照
     * @return MIME 多段体
     * @throws Exception 当正文或附件构造失败时抛出
     */
    private static MimeMultipart createMultipartBody(
            MailRequest request) throws Exception {
        MimeMultipart multipart = new MimeMultipart("mixed");
        MimeBodyPart contentPart = new MimeBodyPart();
        if (request.isHtml()) {
            contentPart.setContent(
                    request.getContent(),
                    "text/html; charset=UTF-8"
            );
        } else {
            contentPart.setText(request.getContent(), "UTF-8");
        }
        multipart.addBodyPart(contentPart);

        for (MailRequest.Attachment attachment : request.getAttachments()) {
            MimeBodyPart attachmentPart = new MimeBodyPart();
            attachmentPart.setDataHandler(
                    new DataHandler(new FileDataSource(attachment.getFile()))
            );
            attachmentPart.setFileName(
                    MimeUtility.encodeText(attachment.getName(), "UTF-8", "B")
            );
            multipart.addBodyPart(attachmentPart);
        }
        return multipart;
    }

    /**
     * 使用 Jakarta Mail 连接并发送消息。
     *
     * @param session Jakarta Mail 会话
     * @param account 已校验账户
     * @param message 待发送消息
     * @throws MessagingException 当连接、认证、发送或关闭失败时抛出
     */
    private static void deliver(
            Session session,
            MailProperties.AccountConfig account,
            MimeMessage message) throws MessagingException {
        String protocol = account.getProtocol().trim().toLowerCase(Locale.ROOT);
        try (Transport transport = session.getTransport(protocol)) {
            if (account.isAuth()) {
                transport.connect(
                        account.getHost(),
                        account.getPort(),
                        account.getUsername(),
                        account.getPassword()
                );
            } else {
                transport.connect(
                        account.getHost(),
                        account.getPort(),
                        null,
                        null
                );
            }
            transport.sendMessage(message, message.getAllRecipients());
        }
    }

    /**
     * 网络传输函数边界。
     */
    @FunctionalInterface
    interface TransportExecutor {

        /**
         * 投递已构造消息。
         *
         * @param session Jakarta Mail 会话
         * @param account 已校验账户
         * @param message 待投递消息
         * @throws MessagingException 当网络投递失败时抛出
         */
        void send(
                Session session,
                MailProperties.AccountConfig account,
                MimeMessage message) throws MessagingException;
    }
}

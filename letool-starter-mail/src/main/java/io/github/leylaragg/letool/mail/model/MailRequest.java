package io.github.leylaragg.letool.mail.model;

import io.github.leylaragg.letool.mail.exception.MailException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 一封待发送邮件的请求模型。
 *
 * <p>新建对象可通过 setter 和添加方法逐步构建。调用 {@link #snapshot()} 后会校验
 * 必填字段、地址和附件，并返回与原对象隔离的不可变快照。邮件门面和默认发送器只会
 * 向底层实现传递快照，从而避免异步投递期间被调用方继续修改。</p>
 */
public class MailRequest {

    /** 当前请求是否允许继续修改。 */
    private final boolean mutable;

    /** 请求指定的 SMTP 账户名称；为空时使用默认账户。 */
    private String accountName;

    /** 请求级发件人地址；为空时使用账户默认地址。 */
    private String from;

    /** 请求级发件人显示名称。 */
    private String personal;

    /** 主收件人地址，保持插入顺序并去重。 */
    private final Set<String> to = new LinkedHashSet<>();

    /** 抄送地址，保持插入顺序并去重。 */
    private final Set<String> cc = new LinkedHashSet<>();

    /** 密送地址，保持插入顺序并去重。 */
    private final Set<String> bcc = new LinkedHashSet<>();

    /** 邮件主题。 */
    private String subject;

    /** 邮件正文。 */
    private String content;

    /** 正文是否使用 HTML 格式。 */
    private boolean html;

    /** 邮件附件。 */
    private final List<Attachment> attachments = new ArrayList<>();

    /**
     * 创建可变邮件请求。
     */
    public MailRequest() {
        this.mutable = true;
    }

    /**
     * 根据现有请求创建不可变快照。
     *
     * @param source 已校验源请求
     */
    private MailRequest(MailRequest source) {
        this.mutable = false;
        this.accountName = source.accountName;
        this.from = source.from;
        this.personal = source.personal;
        this.to.addAll(source.to);
        this.cc.addAll(source.cc);
        this.bcc.addAll(source.bcc);
        this.subject = source.subject;
        this.content = source.content;
        this.html = source.html;
        this.attachments.addAll(source.attachments);
    }

    /**
     * 获取请求指定的账户名称。
     *
     * @return 账户名称；未指定时为 {@code null}
     */
    public String getAccountName() {
        return accountName;
    }

    /**
     * 设置请求使用的账户名称。
     *
     * @param accountName 账户名称；传 {@code null} 表示使用默认账户
     * @throws MailException 当账户名称为空白时抛出
     */
    public void setAccountName(String accountName) {
        requireMutable();
        if (accountName != null && accountName.isBlank()) {
            throw MailException.requestInvalid("account-name");
        }
        this.accountName = accountName;
    }

    /**
     * 获取请求级发件人地址。
     *
     * @return 发件人地址；未指定时为 {@code null}
     */
    public String getFrom() {
        return from;
    }

    /**
     * 设置请求级发件人地址。
     *
     * @param from 发件人地址；传 {@code null} 表示使用账户默认地址
     * @throws MailException 当地址格式不合法时抛出
     */
    public void setFrom(String from) {
        requireMutable();
        if (from != null) {
            validateAddress(from, "from");
        }
        this.from = from;
    }

    /**
     * 获取请求级发件人显示名称。
     *
     * @return 显示名称
     */
    public String getPersonal() {
        return personal;
    }

    /**
     * 设置请求级发件人显示名称。
     *
     * @param personal 显示名称
     */
    public void setPersonal(String personal) {
        requireMutable();
        this.personal = personal;
    }

    /**
     * 获取主收件人的不可修改快照。
     *
     * @return 按插入顺序排列的主收件人
     */
    public Set<String> getTo() {
        return unmodifiableCopy(to);
    }

    /**
     * 获取抄送地址的不可修改快照。
     *
     * @return 按插入顺序排列的抄送地址
     */
    public Set<String> getCc() {
        return unmodifiableCopy(cc);
    }

    /**
     * 获取密送地址的不可修改快照。
     *
     * @return 按插入顺序排列的密送地址
     */
    public Set<String> getBcc() {
        return unmodifiableCopy(bcc);
    }

    /**
     * 获取邮件主题。
     *
     * @return 邮件主题
     */
    public String getSubject() {
        return subject;
    }

    /**
     * 设置邮件主题。
     *
     * @param subject 邮件主题
     */
    public void setSubject(String subject) {
        requireMutable();
        this.subject = subject;
    }

    /**
     * 获取邮件正文。
     *
     * @return 邮件正文
     */
    public String getContent() {
        return content;
    }

    /**
     * 设置邮件正文。
     *
     * @param content 邮件正文
     */
    public void setContent(String content) {
        requireMutable();
        this.content = content;
    }

    /**
     * 判断正文是否使用 HTML 格式。
     *
     * @return HTML 正文返回 {@code true}
     */
    public boolean isHtml() {
        return html;
    }

    /**
     * 设置正文格式。
     *
     * @param html 是否使用 HTML 格式
     */
    public void setHtml(boolean html) {
        requireMutable();
        this.html = html;
    }

    /**
     * 获取附件的不可修改快照。
     *
     * @return 附件列表
     */
    public List<Attachment> getAttachments() {
        return List.copyOf(attachments);
    }

    /**
     * 批量添加主收件人。
     *
     * @param addresses 收件人地址
     * @throws MailException 当地址为空或格式不合法时抛出
     */
    public void addTo(String... addresses) {
        addAddresses(to, "to", addresses);
    }

    /**
     * 批量添加抄送地址。
     *
     * @param addresses 抄送地址
     * @throws MailException 当地址为空或格式不合法时抛出
     */
    public void addCc(String... addresses) {
        addAddresses(cc, "cc", addresses);
    }

    /**
     * 批量添加密送地址。
     *
     * @param addresses 密送地址
     * @throws MailException 当地址为空或格式不合法时抛出
     */
    public void addBcc(String... addresses) {
        addAddresses(bcc, "bcc", addresses);
    }

    /**
     * 添加文件附件。
     *
     * @param name 附件显示名称
     * @param file 附件文件
     * @throws MailException 当名称为空白或文件为 {@code null} 时抛出
     */
    public void addAttachment(String name, File file) {
        requireMutable();
        attachments.add(new Attachment(name, file));
    }

    /**
     * 校验当前请求并创建不可变快照。
     *
     * @return 已校验且不可修改的请求快照
     * @throws MailException 当请求缺少必填字段或附件不可读时抛出
     */
    public MailRequest snapshot() {
        if (!mutable) {
            return this;
        }
        if (to.isEmpty() && cc.isEmpty() && bcc.isEmpty()) {
            throw MailException.requestInvalid("recipients");
        }
        if (subject == null || subject.isBlank()) {
            throw MailException.requestInvalid("subject");
        }
        if (content == null) {
            throw MailException.requestInvalid("content");
        }
        for (Attachment attachment : attachments) {
            if (!attachment.file.isFile() || !attachment.file.canRead()) {
                throw MailException.requestInvalid("attachments");
            }
        }
        return new MailRequest(this);
    }

    /**
     * 将地址集合复制为不可修改且保持顺序的集合。
     *
     * @param source 原始地址集合
     * @return 不可修改地址副本
     */
    private static Set<String> unmodifiableCopy(Set<String> source) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(source));
    }

    /**
     * 校验并原子添加一组地址。
     *
     * @param target 目标地址集合
     * @param field 安全字段名
     * @param addresses 待添加地址
     */
    private void addAddresses(
            Set<String> target,
            String field,
            String... addresses) {
        requireMutable();
        if (addresses == null || addresses.length == 0) {
            throw MailException.requestInvalid(field);
        }
        LinkedHashSet<String> validated = new LinkedHashSet<>();
        for (String address : addresses) {
            validateAddress(address, field);
            validated.add(address);
        }
        target.addAll(validated);
    }

    /**
     * 使用 Jakarta Mail 严格语法校验单个互联网邮箱地址。
     *
     * @param address 待校验地址
     * @param field 安全字段名
     */
    private static void validateAddress(String address, String field) {
        if (address == null || address.isBlank()) {
            throw MailException.requestInvalid(field);
        }
        try {
            InternetAddress[] parsed = InternetAddress.parse(address, true);
            String mailbox = parsed.length == 1 ? parsed[0].getAddress() : null;
            if (mailbox == null
                    || mailbox.indexOf('@') <= 0
                    || mailbox.endsWith("@")) {
                throw MailException.requestInvalid(field);
            }
            parsed[0].validate();
        } catch (AddressException exception) {
            throw MailException.requestInvalid(field);
        }
    }

    /**
     * 确保当前对象仍是可变构建对象。
     */
    private void requireMutable() {
        if (!mutable) {
            throw new UnsupportedOperationException("邮件请求快照不可修改");
        }
    }

    /**
     * 不可变邮件附件描述。
     */
    public static final class Attachment {

        /** 附件显示名称。 */
        private final String name;

        /** 附件文件引用。 */
        private final File file;

        /**
         * 创建附件描述。
         *
         * @param name 附件显示名称
         * @param file 附件文件
         * @throws MailException 当名称为空白或文件为 {@code null} 时抛出
         */
        public Attachment(String name, File file) {
            if (name == null || name.isBlank()) {
                throw MailException.requestInvalid("attachment-name");
            }
            if (file == null) {
                throw MailException.requestInvalid("attachment-file");
            }
            this.name = name;
            this.file = file;
        }

        /**
         * 获取附件显示名称。
         *
         * @return 显示名称
         */
        public String getName() {
            return name;
        }

        /**
         * 获取附件文件。
         *
         * @return 文件引用
         */
        public File getFile() {
            return file;
        }
    }
}

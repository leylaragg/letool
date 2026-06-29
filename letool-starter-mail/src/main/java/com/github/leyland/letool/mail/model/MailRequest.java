package com.github.leyland.letool.mail.model;

import java.io.File;
import java.util.*;

// ======================== 类级别说明 ========================

/**
 * <p>邮件请求模型 — 封装一封邮件的所有构建参数。</p>
 *
 * <h3>职责</h3>
 * <p>{@code MailRequest} 作为邮件模块内部的数据传输对象，
 * 承载从构建器 ({@code MailRequestBuilder}) 到发送器 ({@code MailSender}) 的完整邮件信息。</p>
 *
 * <h3>字段说明</h3>
 * <ul>
 *   <li><b>from / personal</b> — 发件人地址与显示名称（可选，未设置时使用配置中的默认值）。</li>
 *   <li><b>to / cc / bcc</b> — 收件人 / 抄送 / 密送地址集合，使用 {@link LinkedHashSet} 保持插入顺序并自动去重。</li>
 *   <li><b>subject</b> — 邮件主题。</li>
 *   <li><b>content / html</b> — 邮件正文内容及格式标志。</li>
 *   <li><b>templateName / variables</b> — 模板引擎相关：模板名称与键值对变量。</li>
 *   <li><b>attachments</b> — 附件列表，每个附件由名称和 {@link File} 组成。</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <p>通常不由用户直接构造，而是通过 {@code MailTemplate.builder()...} 链式调用间接填充。
 * 如需直接使用，可手动创建实例并通过 setter / add 方法组装。</p>
 *
 * @author leyland
 * @since 1.0.0
 */
public class MailRequest {

    // ======================== 字段定义 ========================

    /** 发件人邮箱地址（可选，回退到配置中的地址） */
    private String from;

    /** 发件人显示名称（可选，回退到配置中的名称） */
    private String personal;

    /** 收件人地址集合（去重，保持插入顺序） */
    private final Set<String> to = new LinkedHashSet<>();

    /** 抄送地址集合（去重，保持插入顺序） */
    private final Set<String> cc = new LinkedHashSet<>();

    /** 密送地址集合（去重，保持插入顺序） */
    private final Set<String> bcc = new LinkedHashSet<>();

    /** 邮件主题 */
    private String subject;

    /** 邮件正文内容 */
    private String content;

    /** 正文是否为 HTML 格式 */
    private boolean html;

    /** 模板变量键值对 */
    private final Map<String, Object> variables = new HashMap<>();

    /** 模板名称 */
    private String templateName;

    /** 附件列表 */
    private final List<Attachment> attachments = new ArrayList<>();

    // ======================== Getter / Setter ========================

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }
    public String getPersonal() { return personal; }
    public void setPersonal(String personal) { this.personal = personal; }
    public Set<String> getTo() { return to; }
    public Set<String> getCc() { return cc; }
    public Set<String> getBcc() { return bcc; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public boolean isHtml() { return html; }
    public void setHtml(boolean html) { this.html = html; }
    public Map<String, Object> getVariables() { return variables; }
    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }
    public List<Attachment> getAttachments() { return attachments; }

    // ======================== 批量添加方法 ========================

    /**
     * 批量添加收件人地址。
     *
     * @param addresses 收件人邮箱地址数组
     */
    public void addTo(String... addresses) { to.addAll(Arrays.asList(addresses)); }

    /**
     * 批量添加抄送地址。
     *
     * @param addresses 抄送邮箱地址数组
     */
    public void addCc(String... addresses) { cc.addAll(Arrays.asList(addresses)); }

    /**
     * 批量添加密送地址。
     *
     * @param addresses 密送邮箱地址数组
     */
    public void addBcc(String... addresses) { bcc.addAll(Arrays.asList(addresses)); }

    /**
     * 添加模板变量。
     *
     * @param key   变量名
     * @param value 变量值
     */
    public void addVariable(String key, Object value) { variables.put(key, value); }

    /**
     * 添加文件附件。
     *
     * @param name 附件显示名称
     * @param file 附件文件
     */
    public void addAttachment(String name, File file) { attachments.add(new Attachment(name, file)); }

    // ======================== 内部类：附件模型 ========================

    /**
     * <p>邮件附件模型 — 封装附件的显示名称与文件引用。</p>
     *
     * <p>附件在 {@link com.github.leyland.letool.mail.core.DefaultMailSender}
     * 中被组装为 Jakarta Mail 的 {@code MimeBodyPart} 并编码文件名。</p>
     */
    public static class Attachment {

        /** 附件显示名称 */
        private final String name;

        /** 附件文件 */
        private final File file;

        /**
         * 构造附件对象。
         *
         * @param name 附件显示名称
         * @param file 附件文件
         */
        public Attachment(String name, File file) { this.name = name; this.file = file; }

        /**
         * 获取附件显示名称。
         *
         * @return 附件显示名称
         */
        public String getName() { return name; }

        /**
         * 获取附件文件。
         *
         * @return 附件文件
         */
        public File getFile() { return file; }
    }
}

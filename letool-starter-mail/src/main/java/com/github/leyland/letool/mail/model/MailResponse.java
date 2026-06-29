package com.github.leyland.letool.mail.model;

import java.time.LocalDateTime;

// ======================== 类级别说明 ========================

/**
 * <p>邮件响应模型 — 封装一次邮件发送操作的结果。</p>
 *
 * <h3>职责</h3>
 * <p>{@code MailResponse} 作为邮件发送操作的返回值，提供对发送结果的统一访问接口。</p>
 *
 * <h3>字段说明</h3>
 * <ul>
 *   <li><b>success</b> — 发送是否成功。</li>
 *   <li><b>messageId</b> — 成功时返回的邮件消息 ID（由 SMTP 服务器分配）。</li>
 *   <li><b>error</b> — 失败时的错误描述信息。</li>
 *   <li><b>sendTime</b> — 响应对象的创建时间（近似于发送完成时间）。</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <p>本类不提供公开构造方法，只能通过静态工厂方法创建：</p>
 * <ul>
 *   <li>{@link #success(String)} — 发送成功时使用。</li>
 *   <li>{@link #fail(String)} — 发送失败时使用。</li>
 * </ul>
 *
 * <h3>典型用法</h3>
 * <pre>{@code
 * MailResponse response = mailTemplate.builder()
 *     .to("user@example.com")
 *     .subject("Test")
 *     .text("Hello")
 *     .send();
 *
 * if (response.isSuccess()) {
 *     log.info("发送成功, messageId={}, time={}", response.getMessageId(), response.getSendTime());
 * } else {
 *     log.error("发送失败: {}", response.getError());
 * }
 * }</pre>
 *
 * @author leyland
 * @since 1.0.0
 */
public class MailResponse {

    // ======================== 字段定义 ========================

    /** 发送是否成功 */
    private final boolean success;

    /** 邮件消息 ID（成功时由 SMTP 服务器分配） */
    private final String messageId;

    /** 失败时的错误描述 */
    private final String error;

    /** 发送时间（响应对象创建时刻） */
    private final LocalDateTime sendTime;

    // ======================== 私有构造方法 ========================

    /**
     * 私有构造方法，只能通过静态工厂方法创建实例。
     *
     * @param success   是否成功
     * @param messageId 消息 ID（成功时有效）
     * @param error     错误信息（失败时有效）
     */
    private MailResponse(boolean success, String messageId, String error) {
        this.success = success;
        this.messageId = messageId;
        this.error = error;
        this.sendTime = LocalDateTime.now();
    }

    // ======================== 静态工厂方法 ========================

    /**
     * 创建发送成功的响应。
     *
     * @param messageId SMTP 服务器返回的消息 ID
     * @return 成功响应实例
     */
    public static MailResponse success(String messageId) {
        return new MailResponse(true, messageId, null);
    }

    /**
     * 创建发送失败的响应。
     *
     * @param error 失败的错误描述
     * @return 失败响应实例
     */
    public static MailResponse fail(String error) {
        return new MailResponse(false, null, error);
    }

    // ======================== Getter ========================

    /**
     * 获取发送是否成功。
     *
     * @return {@code true} 表示发送成功
     */
    public boolean isSuccess() { return success; }

    /**
     * 获取邮件消息 ID。
     *
     * @return 消息 ID，仅在成功时有效；失败时为 {@code null}
     */
    public String getMessageId() { return messageId; }

    /**
     * 获取错误描述。
     *
     * @return 错误信息，仅在失败时有效；成功时为 {@code null}
     */
    public String getError() { return error; }

    /**
     * 获取发送时间。
     *
     * @return 响应对象创建的本地时间（近似于发送完成时间）
     */
    public LocalDateTime getSendTime() { return sendTime; }
}

package com.github.leyland.letool.mail.exception;

// ======================== 类级别说明 ========================

/**
 * <p>邮件异常 — 邮件模块所有异常的根类型。</p>
 *
 * <h3>职责</h3>
 * <p>继承自 {@link RuntimeException}（非受检异常），用于在邮件发送失败时向上抛出，
 * 调用方可根据需要决定是否捕获处理。</p>
 *
 * <h3>抛出场景</h3>
 * <ul>
 *   <li>SMTP 服务器连接失败。</li>
 *   <li>身份认证失败。</li>
 *   <li>邮件内容构建失败。</li>
 *   <li>附件文件读取失败。</li>
 *   <li>异步发送过程异常。</li>
 * </ul>
 *
 * <h3>典型用法</h3>
 * <pre>{@code
 * try {
 *     mailTemplate.builder().to("user@example.com").subject("Test").text("内容").send();
 * } catch (MailException e) {
 *     log.error("邮件发送失败: {}", e.getMessage());
 *     // 执行补偿逻辑
 * }
 * }</pre>
 *
 * @author leyland
 * @since 1.0.0
 */
public class MailException extends RuntimeException {

    // ======================== 构造方法 ========================

    /**
     * 使用消息文本构造邮件异常。
     *
     * @param message 错误消息
     */
    public MailException(String message) {
        super(message);
    }

    /**
     * 使用消息文本和原因异常构造邮件异常。
     *
     * @param message 错误消息
     * @param cause   导致此异常的根本原因
     */
    public MailException(String message, Throwable cause) {
        super(message, cause);
    }
}

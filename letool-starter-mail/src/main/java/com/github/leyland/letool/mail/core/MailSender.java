package com.github.leyland.letool.mail.core;

import com.github.leyland.letool.mail.model.MailRequest;
import com.github.leyland.letool.mail.model.MailResponse;

// ======================== 类级别说明 ========================

/**
 * <p>邮件发送器接口 — 定义邮件发送的核心契约。</p>
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>接收一个 {@link MailRequest} 对象，执行邮件发送并返回 {@link MailResponse}。</li>
 *   <li>所有邮件发送实现（如同步、异步、Mock 等）均应实现此接口。</li>
 * </ul>
 *
 * <h3>典型实现</h3>
 * <ul>
 *   <li>{@link DefaultMailSender} — 基于 Jakarta Mail 的默认实现。</li>
 * </ul>
 *
 * <h3>扩展方式</h3>
 * <p>用户可通过实现此接口并注册为 Spring Bean（配合 {@code @Primary}）来完全接管邮件发送逻辑，
 * 例如对接企业微信、钉钉等非 SMTP 通道。</p>
 *
 * @author leyland
 * @since 1.0.0
 */
public interface MailSender {

    // ======================== 方法定义 ========================

    /**
     * 发送邮件。
     *
     * @param request 邮件请求，包含收件人、主题、内容、附件等完整信息
     * @return 邮件响应，包含成功状态、消息 ID、错误信息等
     */
    MailResponse send(MailRequest request);
}

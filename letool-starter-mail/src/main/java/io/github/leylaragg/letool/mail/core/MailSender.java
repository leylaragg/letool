package io.github.leylaragg.letool.mail.core;

import io.github.leylaragg.letool.mail.model.MailRequest;
import io.github.leylaragg.letool.mail.model.MailResponse;

/**
 * 邮件投递实现的用户扩展接口。
 *
 * <p>默认实现使用 Jakarta Mail 和 SMTP。业务项目可以注册自己的
 * {@code MailSender} Bean 接管投递，例如接入内部邮件网关、审计代理或测试替身。
 * 自动配置会对用户实现退让，不要求自定义发送器同时配置 Letool SMTP 账户。</p>
 *
 * <p>通过 {@link MailTemplate} 调用时，传入实现的是已经校验且不可修改的
 * {@link MailRequest} 快照。实现类不应尝试修改请求，也不应返回 {@code null}。</p>
 */
@FunctionalInterface
public interface MailSender {

    /**
     * 投递一封邮件。
     *
     * @param request 已校验的不可变邮件请求
     * @return 非空邮件响应
     */
    MailResponse send(MailRequest request);
}

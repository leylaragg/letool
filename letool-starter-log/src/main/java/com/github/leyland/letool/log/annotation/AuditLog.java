package com.github.leyland.letool.log.annotation;

import com.github.leyland.letool.log.audit.AuditType;

import java.lang.annotation.*;

/**
 * 审计日志注解 —— 标记在需要记录审计日志的方法上（登录、删除用户、修改权限等关键操作）.
 *
 * <h2>记录内容</h2>
 * <ul>
 *   <li>操作人（默认读取 Servlet Principal，可通过上下文提供器扩展）</li>
 *   <li>操作时间（毫秒精度）</li>
 *   <li>Servlet 容器提供的客户端地址、User-Agent</li>
 *   <li>操作类型（认证 / 管理 / 业务）</li>
 *   <li>业务编号（通过 SpEL 表达式从方法参数中提取）</li>
 *   <li>执行结果（SUCCESS / FAIL）和错误信息</li>
 * </ul>
 *
 * <h2>典型示例</h2>
 * <pre>{@code
 * @AuditLog(operation = "删除用户", type = AuditType.ADMIN, bizNo = "#userId")
 * public void deleteUser(Long userId) { ... }
 *
 * @AuditLog(operation = "创建订单", type = AuditType.BUSINESS, bizNo = "#request.orderNo")
 * public Order createOrder(@RequestBody CreateOrderRequest request) { ... }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AuditLog {

    /**
     * 操作名称 —— 人类可读的操作描述（如 "删除用户"、"修改密码"、"导出报表"）。
     *
     * @return 操作名称
     */
    String operation();

    /**
     * 审计类型 —— 用于分类统计和过滤。
     * AUTH=认证操作（登录/登出），ADMIN=管理操作（增删改），BUSINESS=业务操作（如下单）。
     *
     * @return 审计操作类型
     */
    AuditType type() default AuditType.BUSINESS;

    /**
     * 业务编号 —— 支持 SpEL 表达式从方法参数中提取。
     * 例如 {@code "#userId"} 引用方法参数 userId，{@code "#request.orderNo"}
     * 引用 request 的 orderNo 属性。
     * 为空表示该操作为独立事件，不关联业务单号。
     *
     * @return 业务编号 SpEL
     */
    String bizNo() default "";

    /**
     * 是否记录请求体（方法入参的 JSON 序列化结果）。
     *
     * <p>为避免意外泄露密码、令牌和个人信息，默认关闭。只有确认参数可安全记录时
     * 才应显式开启，并结合自定义 {@code JsonCodec} 完成必要的脱敏。</p>
     *
     * @return {@code true} 表示记录方法参数
     */
    boolean logRequestBody() default false;

    /**
     * 请求体最大记录长度（字符数）—— 超长截断，避免审计日志膨胀。
     *
     * <p>小于或等于零时不记录请求参数。</p>
     *
     * @return 请求参数最大记录长度
     */
    int maxBodyLength() default 1024;
}

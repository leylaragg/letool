package com.github.leyland.letool.log.annotation;

import com.github.leyland.letool.log.audit.AuditType;

import java.lang.annotation.*;

/**
 * 审计日志注解 —— 标记在需要记录审计日志的方法上（登录、删除用户、修改权限等关键操作）.
 *
 * <h3>记录内容</h3>
 * <ul>
 *   <li>操作人（从 SecurityContext 获取当前登录用户）</li>
 *   <li>操作时间（毫秒精度）</li>
 *   <li>客户端 IP、User-Agent</li>
 *   <li>操作类型（认证 / 管理 / 业务）</li>
 *   <li>业务单号（通过 SpEL 表达式从方法参数中提取）</li>
 *   <li>执行结果（SUCCESS / FAIL）和错误信息</li>
 * </ul>
 *
 * <h3>典型示例</h3>
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
     */
    String operation();

    /**
     * 审计类型 —— 用于分类统计和过滤。
     * AUTH=认证操作（登录/登出），ADMIN=管理操作（增删改），BUSINESS=业务操作（如下单）。
     */
    AuditType type() default AuditType.BUSINESS;

    /**
     * 业务单号 —— 支持 SpEL 表达式从方法参数中提取。
     * 例如 {@code "#userId"} 引用方法参数 userId, {@code "#request.orderNo"} 引用 request 的 orderNo 属性。
     * 为空表示该操作为独立事件，不关联业务单号。
     */
    String bizNo() default "";

    /**
     * 是否记录请求体（方法入参的 JSON 序列化结果）。
     * 包含密码等敏感数据的操作建议关闭。
     */
    boolean logRequestBody() default true;

    /**
     * 请求体最大记录长度（字符数）—— 超长截断，避免审计日志膨胀。
     */
    int maxBodyLength() default 1024;
}

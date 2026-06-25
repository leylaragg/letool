package com.github.leyland.letool.log.audit;

import java.time.LocalDateTime;

/**
 * 审计日志事件模型 —— 记录一次操作审计的完整信息.
 *
 * <p>支持 Builder 模式构建: {@code AuditLogEvent.builder().operation("删除用户")...build()}.</p>
 */
public class AuditLogEvent {

    /** 链路追踪 ID —— 关联同一请求链路上的所有日志 */
    private String traceId;

    /** 操作人 —— 当前登录用户的用户名或 ID */
    private String operator;

    /** 操作名称 —— 人类可读的操作描述，如 "删除用户"、"修改密码" */
    private String operation;

    /** 业务单号 —— 关联的业务单据编号（如订单号、用户 ID），通过 SpEL 从方法参数提取 */
    private String bizNo;

    /** 执行结果 —— SUCCESS / FAIL */
    private String result;

    /** 客户端 IP 地址 */
    private String ip;

    /** 浏览器 User-Agent 字符串 */
    private String userAgent;

    /** 执行耗时（毫秒）—— 从方法进入到执行完成的总时间 */
    private Integer durationMs;

    /** 请求体内容 —— 方法入参的 JSON 序列化结果，敏感字段已脱敏 */
    private String requestBody;

    /** 错误信息 —— 仅 result=FAIL 时有值，记录异常消息 */
    private String errorMessage;

    /** 记录创建时间 —— 默认为当前时间 */
    private LocalDateTime createTime = LocalDateTime.now();

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getBizNo() { return bizNo; }
    public void setBizNo(String bizNo) { this.bizNo = bizNo; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer durationMs) { this.durationMs = durationMs; }
    public String getRequestBody() { return requestBody; }
    public void setRequestBody(String requestBody) { this.requestBody = requestBody; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final AuditLogEvent event = new AuditLogEvent();
        public Builder traceId(String v) { event.traceId = v; return this; }
        public Builder operator(String v) { event.operator = v; return this; }
        public Builder operation(String v) { event.operation = v; return this; }
        public Builder bizNo(String v) { event.bizNo = v; return this; }
        public Builder result(String v) { event.result = v; return this; }
        public Builder ip(String v) { event.ip = v; return this; }
        public Builder userAgent(String v) { event.userAgent = v; return this; }
        public Builder durationMs(Integer v) { event.durationMs = v; return this; }
        public Builder requestBody(String v) { event.requestBody = v; return this; }
        public Builder errorMessage(String v) { event.errorMessage = v; return this; }
        public AuditLogEvent build() { return event; }
    }
}

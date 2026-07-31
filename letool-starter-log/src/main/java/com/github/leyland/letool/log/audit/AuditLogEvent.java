package com.github.leyland.letool.log.audit;

import java.time.LocalDateTime;

/**
 * 一次业务操作对应的结构化审计事件。
 *
 * <p>该模型只描述通用审计数据，不携带数据库主键、租户字段或 ORM 注解。业务应用可以
 * 直接消费该对象，也可以在自定义 {@link AuditLogService} 中转换为自己的持久化实体。</p>
 */
public class AuditLogEvent {

    /** 关联同一请求链路上日志的追踪标识。 */
    private String traceId;

    /** 当前操作人的用户名、用户 ID 或其他稳定标识。 */
    private String operator;

    /** 面向审计人员展示的操作名称。 */
    private String operation;

    /** 用于筛选和统计的审计操作类型。 */
    private AuditType type = AuditType.BUSINESS;

    /** 与本次操作关联的业务编号。 */
    private String bizNo;

    /** 执行结果，默认切面使用 {@code SUCCESS} 或 {@code FAIL}。 */
    private String result;

    /** Servlet 容器或自定义上下文提供的客户端地址。 */
    private String ip;

    /** 客户端 User-Agent。 */
    private String userAgent;

    /** 从业务方法进入到结束的执行耗时，单位为毫秒。 */
    private Integer durationMs;

    /** 经显式授权后记录的方法参数 JSON。 */
    private String requestBody;

    /** 业务执行失败时记录的异常摘要。 */
    private String errorMessage;

    /** 审计事件创建时间。 */
    private LocalDateTime createTime = LocalDateTime.now();

    /**
     * 获取链路追踪标识。
     *
     * @return 链路追踪标识
     */
    public String getTraceId() {
        return traceId;
    }

    /**
     * 设置链路追踪标识。
     *
     * @param traceId 链路追踪标识
     */
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    /**
     * 获取当前操作人标识。
     *
     * @return 当前操作人标识
     */
    public String getOperator() {
        return operator;
    }

    /**
     * 设置当前操作人标识。
     *
     * @param operator 当前操作人标识
     */
    public void setOperator(String operator) {
        this.operator = operator;
    }

    /**
     * 获取操作名称。
     *
     * @return 操作名称
     */
    public String getOperation() {
        return operation;
    }

    /**
     * 设置操作名称。
     *
     * @param operation 操作名称
     */
    public void setOperation(String operation) {
        this.operation = operation;
    }

    /**
     * 获取审计操作类型。
     *
     * @return 审计操作类型
     */
    public AuditType getType() {
        return type;
    }

    /**
     * 设置审计操作类型。
     *
     * @param type 审计操作类型
     */
    public void setType(AuditType type) {
        this.type = type;
    }

    /**
     * 获取业务编号。
     *
     * @return 业务编号
     */
    public String getBizNo() {
        return bizNo;
    }

    /**
     * 设置业务编号。
     *
     * @param bizNo 业务编号
     */
    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
    }

    /**
     * 获取执行结果。
     *
     * @return 执行结果
     */
    public String getResult() {
        return result;
    }

    /**
     * 设置执行结果。
     *
     * @param result 执行结果
     */
    public void setResult(String result) {
        this.result = result;
    }

    /**
     * 获取客户端地址。
     *
     * @return 客户端地址
     */
    public String getIp() {
        return ip;
    }

    /**
     * 设置客户端地址。
     *
     * @param ip 客户端地址
     */
    public void setIp(String ip) {
        this.ip = ip;
    }

    /**
     * 获取客户端 User-Agent。
     *
     * @return 客户端 User-Agent
     */
    public String getUserAgent() {
        return userAgent;
    }

    /**
     * 设置客户端 User-Agent。
     *
     * @param userAgent 客户端 User-Agent
     */
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    /**
     * 获取执行耗时。
     *
     * @return 执行耗时，单位为毫秒
     */
    public Integer getDurationMs() {
        return durationMs;
    }

    /**
     * 设置执行耗时。
     *
     * @param durationMs 执行耗时，单位为毫秒
     */
    public void setDurationMs(Integer durationMs) {
        this.durationMs = durationMs;
    }

    /**
     * 获取请求参数 JSON。
     *
     * @return 请求参数 JSON
     */
    public String getRequestBody() {
        return requestBody;
    }

    /**
     * 设置请求参数 JSON。
     *
     * @param requestBody 请求参数 JSON
     */
    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    /**
     * 获取业务异常摘要。
     *
     * @return 业务异常摘要
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * 设置业务异常摘要。
     *
     * @param errorMessage 业务异常摘要
     */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * 获取事件创建时间。
     *
     * @return 事件创建时间
     */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /**
     * 设置事件创建时间。
     *
     * @param createTime 事件创建时间
     */
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    /**
     * 创建审计事件构建器。
     *
     * @return 新的审计事件构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 以链式调用方式构建审计事件。
     */
    public static class Builder {

        private final AuditLogEvent event = new AuditLogEvent();

        /**
         * 设置链路追踪标识。
         *
         * @param traceId 链路追踪标识
         * @return 当前构建器
         */
        public Builder traceId(String traceId) {
            event.traceId = traceId;
            return this;
        }

        /**
         * 设置当前操作人标识。
         *
         * @param operator 当前操作人标识
         * @return 当前构建器
         */
        public Builder operator(String operator) {
            event.operator = operator;
            return this;
        }

        /**
         * 设置操作名称。
         *
         * @param operation 操作名称
         * @return 当前构建器
         */
        public Builder operation(String operation) {
            event.operation = operation;
            return this;
        }

        /**
         * 设置审计操作类型。
         *
         * @param type 审计操作类型
         * @return 当前构建器
         */
        public Builder type(AuditType type) {
            event.type = type;
            return this;
        }

        /**
         * 设置业务编号。
         *
         * @param bizNo 业务编号
         * @return 当前构建器
         */
        public Builder bizNo(String bizNo) {
            event.bizNo = bizNo;
            return this;
        }

        /**
         * 设置执行结果。
         *
         * @param result 执行结果
         * @return 当前构建器
         */
        public Builder result(String result) {
            event.result = result;
            return this;
        }

        /**
         * 设置客户端地址。
         *
         * @param ip 客户端地址
         * @return 当前构建器
         */
        public Builder ip(String ip) {
            event.ip = ip;
            return this;
        }

        /**
         * 设置客户端 User-Agent。
         *
         * @param userAgent 客户端 User-Agent
         * @return 当前构建器
         */
        public Builder userAgent(String userAgent) {
            event.userAgent = userAgent;
            return this;
        }

        /**
         * 设置执行耗时。
         *
         * @param durationMs 执行耗时，单位为毫秒
         * @return 当前构建器
         */
        public Builder durationMs(Integer durationMs) {
            event.durationMs = durationMs;
            return this;
        }

        /**
         * 设置请求参数 JSON。
         *
         * @param requestBody 请求参数 JSON
         * @return 当前构建器
         */
        public Builder requestBody(String requestBody) {
            event.requestBody = requestBody;
            return this;
        }

        /**
         * 设置业务异常摘要。
         *
         * @param errorMessage 业务异常摘要
         * @return 当前构建器
         */
        public Builder errorMessage(String errorMessage) {
            event.errorMessage = errorMessage;
            return this;
        }

        /**
         * 设置事件创建时间。
         *
         * @param createTime 事件创建时间
         * @return 当前构建器
         */
        public Builder createTime(LocalDateTime createTime) {
            event.createTime = createTime;
            return this;
        }

        /**
         * 返回构建完成的审计事件。
         *
         * @return 审计事件
         */
        public AuditLogEvent build() {
            return event;
        }
    }
}

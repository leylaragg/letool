package com.github.leyland.letool.log.audit;

/**
 * 审计日志服务接口.
 */
public interface AuditLogService {

    /**
     * 记录一条审计日志.
     */
    void record(AuditLogEvent event);
}

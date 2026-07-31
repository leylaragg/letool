-- Letool 审计日志 MySQL 8 参考表结构。
--
-- 该脚本仅作为字段和索引规范，不会被 Spring Boot 自动执行。
-- 业务应用可以直接使用，也可以增加租户、数据权限、逻辑删除或自定义主键字段。
CREATE TABLE `letool_audit_log`
(
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '审计日志主键',
    `trace_id`      VARCHAR(64)      NULL COMMENT '链路追踪标识',
    `operator`      VARCHAR(128)     NULL COMMENT '操作人标识',
    `operation`     VARCHAR(255)     NOT NULL COMMENT '操作名称',
    `audit_type`    VARCHAR(32)      NOT NULL COMMENT '审计类型',
    `biz_no`        VARCHAR(128)     NULL COMMENT '业务编号',
    `result`        VARCHAR(16)      NOT NULL COMMENT '执行结果：SUCCESS 或 FAIL',
    `client_ip`     VARCHAR(64)      NULL COMMENT '客户端地址',
    `user_agent`    VARCHAR(512)     NULL COMMENT '客户端 User-Agent',
    `duration_ms`   INT UNSIGNED     NULL COMMENT '执行耗时，单位为毫秒',
    `request_body`  LONGTEXT         NULL COMMENT '显式允许记录的请求参数 JSON',
    `error_message` VARCHAR(2048)    NULL COMMENT '业务异常摘要',
    `create_time`   DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '事件创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_audit_trace_id` (`trace_id`),
    KEY `idx_audit_operator_time` (`operator`, `create_time`),
    KEY `idx_audit_biz_no_time` (`biz_no`, `create_time`),
    KEY `idx_audit_type_time` (`audit_type`, `create_time`),
    KEY `idx_audit_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Letool 业务操作审计日志';

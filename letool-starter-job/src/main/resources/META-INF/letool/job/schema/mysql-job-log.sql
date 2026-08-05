-- Letool Job 执行日志参考表，仅供用户按需执行，框架不会自动创建或修改业务表。
-- 时间字段建议统一写入 UTC；error_message 只保存已截断的安全摘要，不应保存业务参数、密钥或 Token。

CREATE TABLE IF NOT EXISTS `letool_job_execution_log` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `execution_id` VARCHAR(64) NOT NULL COMMENT '同一首次执行及其重试共享的执行标识',
    `job_name` VARCHAR(190) NOT NULL COMMENT '逻辑任务名称',
    `shard_index` INT UNSIGNED NOT NULL COMMENT '分片索引',
    `shard_total` INT UNSIGNED NOT NULL COMMENT '分片总数',
    `scheduler_instance_id` VARCHAR(190) NOT NULL COMMENT 'Quartz 调度节点标识',
    `fire_instance_id` VARCHAR(190) NOT NULL COMMENT 'Quartz 单次触发标识',
    `trigger_type` VARCHAR(32) NOT NULL COMMENT '触发来源：CRON、MANUAL、RETRY、RECOVERY',
    `retry_count` INT UNSIGNED NOT NULL COMMENT '当前重试次数，首次执行为 0',
    `status` VARCHAR(32) NOT NULL COMMENT '执行状态：SUCCESS、RETRY_SCHEDULED、FAILED',
    `scheduled_fire_time` DATETIME(3) NOT NULL COMMENT 'Quartz 计划触发时间（UTC）',
    `start_time` DATETIME(3) NOT NULL COMMENT '实际开始时间（UTC）',
    `end_time` DATETIME(3) NOT NULL COMMENT '结束时间（UTC）',
    `duration_ms` BIGINT UNSIGNED NOT NULL COMMENT '执行耗时毫秒数',
    `error_message` VARCHAR(1024) NULL COMMENT '已截断的安全错误摘要',
    `created_at` TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_letool_job_execution_attempt` (`execution_id`, `retry_count`),
    KEY `idx_letool_job_name_start_time` (`job_name`, `start_time`),
    KEY `idx_letool_job_status_start_time` (`status`, `start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Letool 任务执行日志';

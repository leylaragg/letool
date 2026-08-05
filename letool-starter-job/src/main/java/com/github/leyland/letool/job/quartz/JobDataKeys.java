package com.github.leyland.letool.job.quartz;

/**
 * Letool 写入 Quartz JobDataMap 的内部键。
 */
final class JobDataKeys {

    static final String PREFIX = "letool.internal.";
    static final String PARAMETER_PREFIX = PREFIX + "parameter.";
    static final String VERSION = PREFIX + "version";
    static final String FINGERPRINT = PREFIX + "fingerprint";
    static final String JOB_NAME = PREFIX + "jobName";
    static final String HANDLER_BEAN_NAME = PREFIX + "handlerBeanName";
    static final String CRON = PREFIX + "cron";
    static final String ZONE = PREFIX + "zone";
    static final String DESCRIPTION = PREFIX + "description";
    static final String SHARD_INDEX = PREFIX + "shardIndex";
    static final String SHARD_TOTAL = PREFIX + "shardTotal";
    static final String MAX_RETRIES = PREFIX + "maxRetries";
    static final String BACKOFF_MS = PREFIX + "backoffMs";
    static final String BACKOFF_MULTIPLIER = PREFIX + "backoffMultiplier";
    static final String MAX_BACKOFF_MS = PREFIX + "maxBackoffMs";
    static final String CONCURRENT = PREFIX + "concurrent";
    static final String MISFIRE_POLICY = PREFIX + "misfirePolicy";
    static final String REQUEST_RECOVERY = PREFIX + "requestRecovery";
    static final String EXECUTION_ID = PREFIX + "executionId";
    static final String RETRY_COUNT = PREFIX + "retryCount";
    static final String TRIGGER_TYPE = PREFIX + "triggerType";

    private JobDataKeys() {
    }
}

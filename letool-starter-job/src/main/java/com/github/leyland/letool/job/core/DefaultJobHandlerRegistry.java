package com.github.leyland.letool.job.core;

import com.github.leyland.letool.job.exception.JobErrorCode;
import com.github.leyland.letool.job.exception.JobException;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 基于并发映射的默认任务处理器注册表。
 */
public class DefaultJobHandlerRegistry implements JobHandlerRegistry {

    private final ConcurrentMap<String, JobHandler> handlers = new ConcurrentHashMap<>();

    /**
     * 原子注册处理器，防止同名任务静默覆盖。
     *
     * @param jobName 逻辑任务名称
     * @param handler 业务处理器
     */
    @Override
    public void register(String jobName, JobHandler handler) {
        String normalizedName = requireJobName(jobName);
        if (handler == null) {
            throw new JobException(JobErrorCode.INVALID_HANDLER, normalizedName, "handler 不能为 null");
        }
        JobHandler existing = handlers.putIfAbsent(normalizedName, handler);
        if (existing != null && existing != handler) {
            throw new JobException(JobErrorCode.DEFINITION_CONFLICT, normalizedName, normalizedName);
        }
    }

    /**
     * 获取处理器，不存在时使用稳定错误码失败。
     *
     * @param jobName 逻辑任务名称
     * @return 已注册处理器
     */
    @Override
    public JobHandler getRequired(String jobName) {
        String normalizedName = requireJobName(jobName);
        JobHandler handler = handlers.get(normalizedName);
        if (handler == null) {
            throw new JobException(JobErrorCode.HANDLER_NOT_FOUND, normalizedName, normalizedName);
        }
        return handler;
    }

    /** @param jobName 逻辑任务名称 @return 存在时返回 {@code true} */
    @Override
    public boolean contains(String jobName) {
        return handlers.containsKey(requireJobName(jobName));
    }

    /** @param jobName 逻辑任务名称 */
    @Override
    public void unregister(String jobName) {
        handlers.remove(requireJobName(jobName));
    }

    private String requireJobName(String jobName) {
        if (jobName == null || jobName.isBlank()) {
            throw new JobException(JobErrorCode.INVALID_DEFINITION, null, "jobName 不能为空");
        }
        return jobName.trim();
    }
}

package com.github.leyland.letool.rule.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 批量验证结果
 * 批量数据的验证结果汇总
 *
 * @author leyland
 * @since 2026/02/14
 */
@Data
public class BatchValidationResult {

    /**
     * 批次ID
     */
    private String batchId;

    /**
     * 项目ID
     */
    private String projectId;

    /**
     * 开始时间
     */
    private Date startTime;

    /**
     * 结束时间
     */
    private Date endTime;

    /**
     * 总执行时间（毫秒）
     */
    private long executionTime;

    /**
     * 总数据数
     */
    private int totalCount;

    /**
     * 通过数
     */
    private int passedCount;

    /**
     * 失败数
     */
    private int failedCount;

    /**
     * 排除数
     */
    private int excludedCount;

    /**
     * 各条数据验证结果
     */
    private List<RuleValidationResult> results = new ArrayList<>();

    /**
     * 错误消息
     */
    private String errorMessage;

    /**
     * 创建批次结果
     */
    public static BatchValidationResult create(String batchId, String projectId) {
        BatchValidationResult result = new BatchValidationResult();
        result.setBatchId(batchId);
        result.setProjectId(projectId);
        result.setStartTime(new Date());
        return result;
    }

    /**
     * 完成批次结果
     */
    public void complete() {
        this.setEndTime(new Date());
        if (this.startTime != null) {
            this.executionTime = this.endTime.getTime() - this.startTime.getTime();
        }
    }

    /**
     * 添加验证结果
     */
    public void addResult(RuleValidationResult result) {
        this.results.add(result);
        this.totalCount++;
        if (result.isPassed() && !result.isExcluded()) {
            this.passedCount++;
        } else if (result.isExcluded()) {
            this.excludedCount++;
        } else {
            this.failedCount++;
        }
    }

    /**
     * 获取通过率
     */
    public double getPassRate() {
        if (totalCount == 0) {
            return 0.0;
        }
        return (double) passedCount / totalCount * 100;
    }
}

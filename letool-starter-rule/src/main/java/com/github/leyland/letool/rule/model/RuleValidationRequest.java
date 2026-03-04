package com.github.leyland.letool.rule.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 规则验证请求
 * 用于API方式接收校验请求
 *
 * @author leyland
 * @since 2026/02/14
 */
@Data
public class RuleValidationRequest {

    /**
     * 项目ID（可选，用于关联规则组）
     */
    private String projectId;

    /**
     * 规则组ID列表（可选，指定要校验的规则组）
     */
    private List<Long> ruleGroupIds;

    /**
     * 单条数据校验
     */
    private Map<String, Object> singleData;

    /**
     * 批量数据校验
     */
    private List<Map<String, Object>> batchData;

    /**
     * 执行模式
     * SEQUENTIAL - 串行
     * PARALLEL - 并行
     * BATCH - 批次
     */
    private ExecuteMode executeMode = ExecuteMode.SEQUENTIAL;

    /**
     * 批次大小（仅在BATCH模式下生效）
     */
    private int batchSize = 1000;

    /**
     * 是否返回详细结果
     */
    private boolean returnDetails = true;

    /**
     * 执行模式枚举
     */
    public enum ExecuteMode {
        /**
         * 串行执行
         */
        SEQUENTIAL,
        /**
         * 并行执行
         */
        PARALLEL,
        /**
         * 批次执行
         */
        BATCH
    }
}

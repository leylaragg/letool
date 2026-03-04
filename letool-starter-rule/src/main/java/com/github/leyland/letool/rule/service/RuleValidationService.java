package com.github.leyland.letool.rule.service;

import com.github.leyland.letool.rule.model.BatchValidationResult;
import com.github.leyland.letool.rule.model.RuleExpressionContext;
import com.github.leyland.letool.rule.model.RuleValidationRequest;
import com.github.leyland.letool.rule.model.RuleValidationResult;
import com.github.leyland.letool.rule.operation.OperationResult;

import java.util.List;
import java.util.Map;

/**
 * 规则验证服务接口
 * 提供规则校验的核心功能
 *
 * @author leyland
 * @since 2026/02/14
 */
public interface RuleValidationService {

    // ==================== 单数据校验 ====================

    /**
     * 验证单条数据
     *
     * @param data       待验证数据
     * @param ruleGroups 规则组列表
     * @return 验证结果
     */
    RuleValidationResult validateSingle(Map<String, Object> data, List<Long> ruleGroupIds);

    /**
     * 验证单条数据（指定项目）
     *
     * @param projectId 项目ID
     * @param data      待验证数据
     * @return 验证结果
     */
    RuleValidationResult validateSingle(String projectId, Map<String, Object> data);

    // ==================== 批量数据校验 ====================

    /**
     * 批量验证数据
     *
     * @param request 验证请求
     * @return 批量验证结果
     */
    BatchValidationResult validateBatch(RuleValidationRequest request);

    /**
     * 批量验证数据（指定项目）
     *
     * @param projectId 项目ID
     * @param dataList  数据列表
     * @return 批量验证结果
     */
    BatchValidationResult validateBatch(String projectId, List<Map<String, Object>> dataList);

    // ==================== 数据库筛选校验 ====================

    /**
     * 数据库筛选校验
     * 从配置的数据源读取数据进行规则校验
     *
     * @param projectId 项目ID
     * @return 批量验证结果
     */
    BatchValidationResult validateFromDatabase(String projectId);

    /**
     * 数据库筛选校验（异步）
     *
     * @param projectId 项目ID
     * @return 批次ID
     */
    String validateFromDatabaseAsync(String projectId);

    // ==================== 规则表达式执行 ====================

    /**
     * 执行规则表达式
     *
     * @param context 规则表达式上下文
     * @return 运算结果
     */
    OperationResult executeExpression(RuleExpressionContext context);

    /**
     * 执行规则表达式（便捷方法）
     *
     * @param expressionId 表达式ID
     * @param data         数据
     * @return 运算结果
     */
    OperationResult executeExpression(Long expressionId, Map<String, Object> data);

    // ==================== 规则组校验 ====================

    /**
     * 校验规则组
     *
     * @param data        待验证数据
     * @param ruleGroupId 规则组ID
     * @return 规则组验证结果
     */
    RuleValidationResult.RuleGroupValidationResult validateRuleGroup(
            Map<String, Object> data, Long ruleGroupId);

    // ==================== 工具方法 ====================

    /**
     * 生成批次ID
     */
    String generateBatchId();

    /**
     * 获取验证统计
     *
     * @param batchId 批次ID
     * @return 统计信息
     */
    Map<String, Object> getValidationStatistics(String batchId);
}

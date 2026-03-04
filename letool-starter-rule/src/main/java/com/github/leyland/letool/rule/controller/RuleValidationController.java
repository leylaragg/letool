package com.github.leyland.letool.rule.controller;

import com.github.leyland.letool.rule.model.BatchValidationResult;
import com.github.leyland.letool.rule.model.RuleValidationRequest;
import com.github.leyland.letool.rule.model.RuleValidationResult;
import com.github.leyland.letool.rule.operation.OperationResult;
import com.github.leyland.letool.rule.service.RuleDictService;
import com.github.leyland.letool.rule.service.RuleValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 规则验证控制器
 * 提供REST API接口
 *
 * @author leyland
 * @since 2026/02/14
 */
@Slf4j
@RestController
@RequestMapping("/api/rule")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "letool.rule", name = "web-enabled", havingValue = "true", matchIfMissing = true)
public class RuleValidationController {

    private final RuleValidationService validationService;
    private final RuleDictService dictService;

    // ==================== 规则验证接口 ====================

    /**
     * 验证单条数据
     *
     * @param request 验证请求
     * @return 验证结果
     */
    @PostMapping("/validate/single")
    public Result<RuleValidationResult> validateSingle(@RequestBody RuleValidationRequest request) {
        log.info("接收单条数据验证请求: projectId={}", request.getProjectId());
        RuleValidationResult result = validationService.validateSingle(
                request.getProjectId(),
                request.getSingleData()
        );
        return Result.success(result);
    }

    /**
     * 验证单条数据（指定规则组）
     *
     * @param data         待验证数据
     * @param ruleGroupIds 规则组ID列表
     * @return 验证结果
     */
    @PostMapping("/validate/single/groups")
    public Result<RuleValidationResult> validateSingleWithGroups(
            @RequestBody Map<String, Object> data,
            @RequestParam List<Long> ruleGroupIds) {
        log.info("接收单条数据验证请求: ruleGroupIds={}", ruleGroupIds);
        RuleValidationResult result = validationService.validateSingle(data, ruleGroupIds);
        return Result.success(result);
    }

    /**
     * 批量验证数据
     *
     * @param request 验证请求
     * @return 批量验证结果
     */
    @PostMapping("/validate/batch")
    public Result<BatchValidationResult> validateBatch(@RequestBody RuleValidationRequest request) {
        log.info("接收批量数据验证请求: projectId={}, dataSize={}",
                request.getProjectId(),
                request.getBatchData() != null ? request.getBatchData().size() : 0);
        BatchValidationResult result = validationService.validateBatch(request);
        return Result.success(result);
    }

    /**
     * 执行单条规则表达式
     *
     * @param expressionId 表达式ID
     * @param data         数据
     * @return 运算结果
     */
    @PostMapping("/expression/{expressionId}/execute")
    public Result<OperationResult> executeExpression(
            @PathVariable Long expressionId,
            @RequestBody Map<String, Object> data) {
        log.info("执行规则表达式: expressionId={}", expressionId);
        OperationResult result = validationService.executeExpression(expressionId, data);
        return Result.success(result);
    }

    /**
     * 数据库筛选校验
     *
     * @param projectId 项目ID
     * @return 批量验证结果
     */
    @PostMapping("/validate/database/{projectId}")
    public Result<BatchValidationResult> validateFromDatabase(@PathVariable String projectId) {
        log.info("数据库筛选校验请求: projectId={}", projectId);
        try {
            BatchValidationResult result = validationService.validateFromDatabase(projectId);
            return Result.success(result);
        } catch (UnsupportedOperationException e) {
            return Result.error("数据库筛选校验需要配置数据源: " + e.getMessage());
        }
    }

    /**
     * 异步数据库筛选校验
     *
     * @param projectId 项目ID
     * @return 批次ID
     */
    @PostMapping("/validate/database/{projectId}/async")
    public Result<String> validateFromDatabaseAsync(@PathVariable String projectId) {
        log.info("异步数据库筛选校验请求: projectId={}", projectId);
        String batchId = validationService.validateFromDatabaseAsync(projectId);
        return Result.success(batchId);
    }

    /**
     * 获取验证统计
     *
     * @param batchId 批次ID
     * @return 统计信息
     */
    @GetMapping("/validate/statistics/{batchId}")
    public Result<Map<String, Object>> getValidationStatistics(@PathVariable String batchId) {
        Map<String, Object> statistics = validationService.getValidationStatistics(batchId);
        return Result.success(statistics);
    }

    // ==================== 字典管理接口 ====================

    /**
     * 获取所有规则组
     */
    @GetMapping("/groups")
    public Result<List<?>> getAllRuleGroups() {
        return Result.success(dictService.getAllRuleGroups());
    }

    /**
     * 根据项目ID获取规则组
     */
    @GetMapping("/groups/project/{projectId}")
    public Result<List<?>> getRuleGroupsByProjectId(@PathVariable String projectId) {
        return Result.success(dictService.getRuleGroupsByProjectId(projectId));
    }

    /**
     * 获取所有运算符
     */
    @GetMapping("/operators")
    public Result<List<?>> getAllOperators() {
        return Result.success(dictService.getAllOperators());
    }

    /**
     * 获取所有字段类型
     */
    @GetMapping("/field-types")
    public Result<List<?>> getAllFieldTypes() {
        return Result.success(dictService.getAllFieldTypes());
    }

    /**
     * 刷新缓存
     */
    @PostMapping("/cache/refresh")
    public Result<Void> refreshCache() {
        dictService.refreshCache();
        return Result.success();
    }

    // ==================== 统一响应对象 ====================

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class Result<T> {
        private int code;
        private String message;
        private T data;

        public static <T> Result<T> success(T data) {
            return new Result<>(200, "success", data);
        }

        public static <T> Result<T> success() {
            return new Result<>(200, "success", null);
        }

        public static <T> Result<T> error(String message) {
            return new Result<>(500, message, null);
        }
    }
}

package com.github.leyland.letool.rule.service.impl;

import com.alibaba.fastjson2.JSON;
import com.github.leyland.letool.rule.entity.*;
import com.github.leyland.letool.rule.model.*;
import com.github.leyland.letool.rule.operation.*;
import com.github.leyland.letool.rule.service.RuleDictService;
import com.github.leyland.letool.rule.service.RuleValidationService;
import com.github.leyland.letool.rule.validator.RuleExpressionValidator;
import com.github.leyland.letool.rule.validator.ValidationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 规则验证服务实现
 * 核心规则校验逻辑
 *
 * @author leyland
 * @since 2026/02/14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleValidationServiceImpl implements RuleValidationService {

    private final RuleDictCacheServiceImpl cacheService;
    private final RuleDictService ruleDictService;
    private final RuleExpressionValidator expressionValidator;
    private final OperationExecutorFactory operationExecutorFactory;
    private final TypeConverter typeConverter;

    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private final Map<String, BatchValidationResult> batchResultCache = new ConcurrentHashMap<>();

    // ==================== 单数据校验 ====================

    @Override
    public RuleValidationResult validateSingle(Map<String, Object> data, List<Long> ruleGroupIds) {
        long startTime = System.currentTimeMillis();
        String dataId = generateDataId(data);

        RuleValidationResult result = new RuleValidationResult();
        result.setDataId(dataId);
        result.setValidationTime(new Date());

        if (data == null || data.isEmpty()) {
            return RuleValidationResult.failure(dataId, "待验证数据不能为空");
        }

        if (ruleGroupIds == null || ruleGroupIds.isEmpty()) {
            return RuleValidationResult.failure(dataId, "规则组ID列表不能为空");
        }

        try {
            // 分类规则组
            List<RuleGroup> includeGroups = new ArrayList<>();
            List<RuleGroup> excludeGroups = new ArrayList<>();

            for (Long groupId : ruleGroupIds) {
                RuleGroup group = cacheService.getRuleGroupById(groupId);
                if (group != null && Boolean.TRUE.equals(group.getEnabled())) {
                    List<RuleExpression> expressions = cacheService.getExpressionsByGroupId(groupId);
                    group.setRuleExpressions(expressions);

                    if (group.getGroupType() == null || group.getGroupType() == 0) {
                        includeGroups.add(group);
                    } else {
                        excludeGroups.add(group);
                    }
                }
            }

            // 执行验证
            boolean passed = executeValidation(data, includeGroups, excludeGroups, result);

            result.setPassed(passed);
            result.setExecutionTime(System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            log.error("规则验证异常: dataId={}", dataId, e);
            result.setPassed(false);
            result.setErrorMessage("规则验证异常: " + e.getMessage());
        }

        return result;
    }

    @Override
    public RuleValidationResult validateSingle(String projectId, Map<String, Object> data) {
        List<RuleGroup> groups = cacheService.getRuleGroupsByProjectId(projectId);
        List<Long> groupIds = groups.stream()
                .filter(g -> Boolean.TRUE.equals(g.getEnabled()))
                .map(RuleGroup::getId)
                .collect(Collectors.toList());
        return validateSingle(data, groupIds);
    }

    // ==================== 批量数据校验 ====================

    @Override
    public BatchValidationResult validateBatch(RuleValidationRequest request) {
        String batchId = generateBatchId();
        BatchValidationResult batchResult = BatchValidationResult.create(batchId, request.getProjectId());

        List<Map<String, Object>> dataList = new ArrayList<>();
        if (request.getSingleData() != null) {
            dataList.add(request.getSingleData());
        }
        if (request.getBatchData() != null) {
            dataList.addAll(request.getBatchData());
        }

        if (dataList.isEmpty()) {
            batchResult.setErrorMessage("待验证数据不能为空");
            batchResult.complete();
            return batchResult;
        }

        // 获取规则组
        List<Long> groupIds = request.getRuleGroupIds();
        if ((groupIds == null || groupIds.isEmpty()) && request.getProjectId() != null) {
            List<RuleGroup> groups = cacheService.getRuleGroupsByProjectId(request.getProjectId());
            groupIds = groups.stream()
                    .filter(g -> Boolean.TRUE.equals(g.getEnabled()))
                    .map(RuleGroup::getId)
                    .collect(Collectors.toList());
        }

        // 根据执行模式选择处理方式
        switch (request.getExecuteMode()) {
            case PARALLEL:
                validateBatchParallel(dataList, groupIds, batchResult);
                break;
            case BATCH:
                validateBatchInBatches(dataList, groupIds, batchResult, request.getBatchSize());
                break;
            default:
                validateBatchSequential(dataList, groupIds, batchResult);
        }

        batchResult.complete();
        return batchResult;
    }

    @Override
    public BatchValidationResult validateBatch(String projectId, List<Map<String, Object>> dataList) {
        RuleValidationRequest request = new RuleValidationRequest();
        request.setProjectId(projectId);
        request.setBatchData(dataList);
        return validateBatch(request);
    }

    // ==================== 数据库筛选校验 ====================

    @Override
    public BatchValidationResult validateFromDatabase(String projectId) {
        // 此方法需要配合 letool-starter-data 模块使用
        // 实际实现时需要注入 DatabaseQueryService
        throw new UnsupportedOperationException("数据库筛选校验需要配置数据源，请参考文档");
    }

    @Override
    @Async
    public String validateFromDatabaseAsync(String projectId) {
        String batchId = generateBatchId();
        CompletableFuture.runAsync(() -> {
            try {
                BatchValidationResult result = validateFromDatabase(projectId);
                batchResultCache.put(batchId, result);
            } catch (Exception e) {
                log.error("异步数据库筛选校验失败: projectId={}", projectId, e);
            }
        }, executorService);
        return batchId;
    }

    // ==================== 规则表达式执行 ====================

    @Override
    public OperationResult executeExpression(RuleExpressionContext context) {
        long startTime = System.currentTimeMillis();

        try {
            // 获取表达式、字段、运算符信息
            RuleExpression expression = context.getExpression();
            RuleFieldDict fieldDict = context.getFieldDict();
            RuleFieldType fieldType = context.getFieldType();
            RuleOperator operator = context.getOperator();

            if (expression == null || fieldDict == null || operator == null) {
                return OperationResult.error("表达式上下文信息不完整");
            }

            // 验证表达式
            ValidationResult validationResult = expressionValidator.validate(context);
            if (!validationResult.isSuccess()) {
                return OperationResult.error("表达式验证失败: " + validationResult.getMessage());
            }

            // 获取实际值
            Object actualValue = context.getFieldValue();
            Object compareValue = expression.getCompareValue();

            // 构建运算请求
            OperationRequest request = OperationRequest.builder()
                    .leftOperand(actualValue)
                    .leftType(fieldType != null ? fieldType.getTypeCode() : "STRING")
                    .rightOperand(compareValue)
                    .rightType(fieldType != null ? fieldType.getTypeCode() : "STRING")
                    .operator(operator.getOperatorCode())
                    .fieldName(fieldDict.getFieldName())
                    .ruleDescription(expression.getDescription())
                    .build();

            // 执行运算
            return operationExecutorFactory.execute(request);

        } catch (Exception e) {
            log.error("执行表达式异常", e);
            return OperationResult.error("执行表达式异常: " + e.getMessage())
                    .executionTime(System.currentTimeMillis() - startTime);
        }
    }

    @Override
    public OperationResult executeExpression(Long expressionId, Map<String, Object> data) {
        RuleExpression expression = cacheService.getExpressionById(expressionId);
        if (expression == null) {
            return OperationResult.error("规则表达式不存在: " + expressionId);
        }

        // 构建上下文
        RuleExpressionContext context = buildExpressionContext(expression, data);
        return executeExpression(context);
    }

    // ==================== 规则组校验 ====================

    @Override
    public RuleValidationResult.RuleGroupValidationResult validateRuleGroup(
            Map<String, Object> data, Long ruleGroupId) {
        long startTime = System.currentTimeMillis();

        RuleGroup group = cacheService.getRuleGroupById(ruleGroupId);
        if (group == null) {
            RuleValidationResult.RuleGroupValidationResult result = new RuleValidationResult.RuleGroupValidationResult();
            result.setPassed(false);
            return result;
        }

        List<RuleExpression> expressions = cacheService.getExpressionsByGroupId(ruleGroupId);
        return doValidateRuleGroup(data, group, expressions, startTime);
    }

    // ==================== 工具方法 ====================

    @Override
    public String generateBatchId() {
        return "BATCH_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Override
    public Map<String, Object> getValidationStatistics(String batchId) {
        Map<String, Object> statistics = new HashMap<>();
        BatchValidationResult result = batchResultCache.get(batchId);
        if (result != null) {
            statistics.put("batchId", result.getBatchId());
            statistics.put("totalCount", result.getTotalCount());
            statistics.put("passedCount", result.getPassedCount());
            statistics.put("failedCount", result.getFailedCount());
            statistics.put("excludedCount", result.getExcludedCount());
            statistics.put("passRate", result.getPassRate());
            statistics.put("executionTime", result.getExecutionTime());
        }
        return statistics;
    }

    // ==================== 私有方法 ====================

    /**
     * 执行验证
     */
    private boolean executeValidation(Map<String, Object> data,
                                       List<RuleGroup> includeGroups,
                                       List<RuleGroup> excludeGroups,
                                       RuleValidationResult result) {
        // 验证纳入规则组
        boolean includePassed = true;
        for (RuleGroup group : includeGroups) {
            RuleValidationResult.RuleGroupValidationResult groupResult = doValidateRuleGroup(
                    data, group, group.getRuleExpressions(), System.currentTimeMillis());
            result.getGroupResults().add(groupResult);

            if (!groupResult.isPassed() && groupResult.getStopOnFailure() == 1) {
                includePassed = false;
                break;
            }
        }

        result.setPassed(includePassed);

        if (!includePassed) {
            return false;
        }

        // 验证排除规则组
        boolean excluded = false;
        for (RuleGroup group : excludeGroups) {
            RuleValidationResult.RuleGroupValidationResult groupResult = doValidateRuleGroup(
                    data, group, group.getRuleExpressions(), System.currentTimeMillis());
            result.getGroupResults().add(groupResult);

            if (groupResult.isPassed() && groupResult.getStopOnFailure() == 1) {
                excluded = true;
                break;
            }
        }

        result.setExcluded(excluded);
        return !excluded;
    }

    /**
     * 验证规则组
     */
    private RuleValidationResult.RuleGroupValidationResult doValidateRuleGroup(
            Map<String, Object> data, RuleGroup group,
            List<RuleExpression> expressions, long startTime) {

        RuleValidationResult.RuleGroupValidationResult result = new RuleValidationResult.RuleGroupValidationResult();
        result.setRuleGroupId(group.getId());
        result.setRuleGroupName(group.getName());
        result.setGroupType(group.getGroupType());
        result.setStopOnFailure(group.getStopOnFailure());

        if (expressions == null || expressions.isEmpty()) {
            result.setPassed(true);
            result.setExecutionTime(System.currentTimeMillis() - startTime);
            return result;
        }

        // 验证组内每条规则
        boolean allAndPassed = true;
        boolean anyOrPassed = false;

        for (RuleExpression expression : expressions) {
            RuleValidationResult.RuleExpressionResult expResult = doValidateExpression(data, expression);
            result.getExpressionResults().add(expResult);

            int stopOnFailure = expression.getStopOnFailure() != null ? expression.getStopOnFailure() : 1;

            if (stopOnFailure == 1) {
                // 且关系
                if (!expResult.isMatched()) {
                    allAndPassed = false;
                    break;
                }
            } else {
                // 或关系
                if (expResult.isMatched()) {
                    anyOrPassed = true;
                    break;
                }
            }
        }

        // 判断组是否通过
        boolean hasOrRelation = expressions.stream()
                .anyMatch(e -> e.getStopOnFailure() != null && e.getStopOnFailure() == 0);

        if (hasOrRelation) {
            result.setPassed(anyOrPassed);
        } else {
            result.setPassed(allAndPassed);
        }

        result.setExecutionTime(System.currentTimeMillis() - startTime);
        return result;
    }

    /**
     * 验证单条表达式
     */
    private RuleValidationResult.RuleExpressionResult doValidateExpression(
            Map<String, Object> data, RuleExpression expression) {
        long startTime = System.currentTimeMillis();

        RuleValidationResult.RuleExpressionResult result = new RuleValidationResult.RuleExpressionResult();
        result.setExpressionId(expression.getId());
        result.setStopOnFailure(expression.getStopOnFailure());
        result.setCompareValue(expression.getCompareValue());

        try {
            // 获取相关信息
            RuleDict ruleDict = cacheService.getRuleById(expression.getRuleId());
            RuleFieldDict fieldDict = cacheService.getFieldById(expression.getRuleFieldId());
            RuleFieldType fieldType = cacheService.getFieldTypeById(fieldDict != null ? fieldDict.getFieldTypeId() : null);
            RuleOperator operator = cacheService.getOperatorById(expression.getOperatorId());

            result.setRuleName(ruleDict != null ? ruleDict.getRuleName() : null);
            result.setFieldName(fieldDict != null ? fieldDict.getFieldName() : null);
            result.setOperatorName(operator != null ? operator.getOperatorName() : null);
            result.setOperatorSymbol(operator != null ? operator.getOperatorSymbol() : null);

            // 获取实际值
            Object actualValue = null;
            if (ruleDict != null && fieldDict != null) {
                String queryKey = ruleDict.getQueryKey();
                if (data.containsKey(queryKey)) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> tableData = (Map<String, Object>) data.get(queryKey);
                    if (tableData != null) {
                        actualValue = tableData.get(fieldDict.getDatabaseFieldCode());
                    }
                } else {
                    // 直接从数据中获取
                    actualValue = data.get(fieldDict.getDatabaseFieldCode());
                }
            }
            result.setActualValue(actualValue);

            // 构建上下文并执行
            RuleExpressionContext context = RuleExpressionContext.builder()
                    .expression(expression)
                    .ruleDict(ruleDict)
                    .fieldDict(fieldDict)
                    .fieldType(fieldType)
                    .operator(operator)
                    .data(data)
                    .build();

            OperationResult opResult = executeExpression(context);
            result.setMatched(opResult.isMatched());

            if (!opResult.isSuccess()) {
                result.setErrorMessage(opResult.getMessage());
            }

        } catch (Exception e) {
            log.error("验证表达式异常: expressionId={}", expression.getId(), e);
            result.setMatched(false);
            result.setErrorMessage("验证异常: " + e.getMessage());
        }

        result.setExecutionTime(System.currentTimeMillis() - startTime);
        return result;
    }

    /**
     * 构建表达式上下文
     */
    private RuleExpressionContext buildExpressionContext(RuleExpression expression, Map<String, Object> data) {
        RuleDict ruleDict = cacheService.getRuleById(expression.getRuleId());
        RuleFieldDict fieldDict = cacheService.getFieldById(expression.getRuleFieldId());
        RuleFieldType fieldType = fieldDict != null ? cacheService.getFieldTypeById(fieldDict.getFieldTypeId()) : null;
        RuleOperator operator = cacheService.getOperatorById(expression.getOperatorId());
        RuleFlowDict ruleFlow = cacheService.getRuleFlowById(expression.getRuleFlowId());

        return RuleExpressionContext.builder()
                .expression(expression)
                .ruleDict(ruleDict)
                .fieldDict(fieldDict)
                .fieldType(fieldType)
                .operator(operator)
                .ruleFlow(ruleFlow)
                .data(data)
                .build();
    }

    /**
     * 生成数据ID
     */
    private String generateDataId(Map<String, Object> data) {
        if (data == null) {
            return "DATA_" + System.currentTimeMillis();
        }
        // 尝试获取常见标识字段
        String[] idFields = {"id", "patientId", "patient_id", "subjectId", "subject_id"};
        for (String field : idFields) {
            if (data.containsKey(field) && data.get(field) != null) {
                return String.valueOf(data.get(field));
            }
        }
        return "DATA_" + data.hashCode();
    }

    /**
     * 串行批量验证
     */
    private void validateBatchSequential(List<Map<String, Object>> dataList,
                                          List<Long> groupIds,
                                          BatchValidationResult batchResult) {
        for (Map<String, Object> data : dataList) {
            RuleValidationResult result = validateSingle(data, groupIds);
            batchResult.addResult(result);
        }
    }

    /**
     * 并行批量验证
     */
    private void validateBatchParallel(List<Map<String, Object>> dataList,
                                        List<Long> groupIds,
                                        BatchValidationResult batchResult) {
        AtomicInteger counter = new AtomicInteger(0);
        List<RuleValidationResult> results = dataList.parallelStream()
                .map(data -> {
                    RuleValidationResult result = validateSingle(data, groupIds);
                    int count = counter.incrementAndGet();
                    if (count % 100 == 0) {
                        log.info("并行验证进度: {}/{}", count, dataList.size());
                    }
                    return result;
                })
                .collect(Collectors.toList());

        results.forEach(batchResult::addResult);
    }

    /**
     * 分批验证
     */
    private void validateBatchInBatches(List<Map<String, Object>> dataList,
                                         List<Long> groupIds,
                                         BatchValidationResult batchResult,
                                         int batchSize) {
        int totalSize = dataList.size();
        int totalBatches = (int) Math.ceil((double) totalSize / batchSize);

        List<CompletableFuture<List<RuleValidationResult>>> futures = new ArrayList<>();

        for (int i = 0; i < totalBatches; i++) {
            int fromIndex = i * batchSize;
            int toIndex = Math.min(fromIndex + batchSize, totalSize);
            List<Map<String, Object>> batch = dataList.subList(fromIndex, toIndex);

            final int batchNum = i + 1;
            CompletableFuture<List<RuleValidationResult>> future = CompletableFuture.supplyAsync(() -> {
                log.info("执行批次 {}/{}", batchNum, totalBatches);
                return batch.stream()
                        .map(data -> validateSingle(data, groupIds))
                        .collect(Collectors.toList());
            }, executorService);

            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        for (CompletableFuture<List<RuleValidationResult>> future : futures) {
            try {
                future.get().forEach(batchResult::addResult);
            } catch (Exception e) {
                log.error("获取批次结果异常", e);
            }
        }
    }
}

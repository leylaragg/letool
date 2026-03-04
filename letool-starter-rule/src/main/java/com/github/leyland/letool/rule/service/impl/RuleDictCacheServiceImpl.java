package com.github.leyland.letool.rule.service.impl;

import com.github.leyland.letool.rule.entity.*;
import com.github.leyland.letool.rule.service.RuleDictService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 字典缓存服务实现
 * 使用内存缓存存储字典数据
 *
 * @author leyland
 * @since 2026/02/14
 */
@Slf4j
@Service
public class RuleDictCacheServiceImpl {

    /**
     * 规则流缓存
     */
    private final Map<Long, RuleFlowDict> ruleFlowCache = new ConcurrentHashMap<>();

    /**
     * 规则字典缓存
     */
    private final Map<Long, RuleDict> ruleCache = new ConcurrentHashMap<>();
    private final Map<String, RuleDict> ruleByQueryKeyCache = new ConcurrentHashMap<>();

    /**
     * 字段字典缓存
     */
    private final Map<Long, RuleFieldDict> fieldCache = new ConcurrentHashMap<>();
    private final Map<Long, List<RuleFieldDict>> fieldByRuleIdCache = new ConcurrentHashMap<>();

    /**
     * 字段类型缓存
     */
    private final Map<Long, RuleFieldType> fieldTypeCache = new ConcurrentHashMap<>();
    private final Map<String, RuleFieldType> fieldTypeByCodeCache = new ConcurrentHashMap<>();

    /**
     * 运算符缓存
     */
    private final Map<Long, RuleOperator> operatorCache = new ConcurrentHashMap<>();
    private final Map<String, RuleOperator> operatorByCodeCache = new ConcurrentHashMap<>();

    /**
     * 规则组缓存
     */
    private final Map<Long, RuleGroup> ruleGroupCache = new ConcurrentHashMap<>();
    private final Map<String, List<RuleGroup>> ruleGroupByProjectCache = new ConcurrentHashMap<>();

    /**
     * 规则表达式缓存
     */
    private final Map<Long, RuleExpression> expressionCache = new ConcurrentHashMap<>();
    private final Map<Long, List<RuleExpression>> expressionByGroupIdCache = new ConcurrentHashMap<>();

    private final RuleDictService ruleDictService;

    public RuleDictCacheServiceImpl(RuleDictService ruleDictService) {
        this.ruleDictService = ruleDictService;
    }

    @PostConstruct
    public void init() {
        log.info("初始化规则字典缓存...");
        refreshAllCache();
        log.info("规则字典缓存初始化完成");
    }

    /**
     * 刷新所有缓存
     */
    public void refreshAllCache() {
        clearAllCache();
        loadAllCache();
    }

    /**
     * 清除所有缓存
     */
    private void clearAllCache() {
        ruleFlowCache.clear();
        ruleCache.clear();
        ruleByQueryKeyCache.clear();
        fieldCache.clear();
        fieldByRuleIdCache.clear();
        fieldTypeCache.clear();
        fieldTypeByCodeCache.clear();
        operatorCache.clear();
        operatorByCodeCache.clear();
        ruleGroupCache.clear();
        ruleGroupByProjectCache.clear();
        expressionCache.clear();
        expressionByGroupIdCache.clear();
    }

    /**
     * 加载所有缓存
     */
    private void loadAllCache() {
        try {
            // 加载规则流
            List<RuleFlowDict> ruleFlows = ruleDictService.getAllRuleFlows();
            ruleFlows.forEach(flow -> ruleFlowCache.put(flow.getId(), flow));

            // 加载规则字典
            List<RuleDict> rules = ruleDictService.getAllRules();
            rules.forEach(rule -> {
                ruleCache.put(rule.getId(), rule);
                if (rule.getQueryKey() != null) {
                    ruleByQueryKeyCache.put(rule.getQueryKey(), rule);
                }
            });

            // 加载字段字典
            List<RuleFieldDict> fields = ruleDictService.getAllFields();
            fields.forEach(field -> {
                fieldCache.put(field.getId(), field);
                fieldByRuleIdCache.computeIfAbsent(field.getRuleId(), k -> new ArrayList<>())
                        .add(field);
            });

            // 加载字段类型
            List<RuleFieldType> fieldTypes = ruleDictService.getAllFieldTypes();
            fieldTypes.forEach(type -> {
                fieldTypeCache.put(type.getId(), type);
                if (type.getTypeCode() != null) {
                    fieldTypeByCodeCache.put(type.getTypeCode().toUpperCase(), type);
                }
            });

            // 加载运算符
            List<RuleOperator> operators = ruleDictService.getAllOperators();
            operators.forEach(op -> {
                operatorCache.put(op.getId(), op);
                if (op.getOperatorCode() != null) {
                    operatorByCodeCache.put(op.getOperatorCode(), op);
                }
            });

            // 加载规则组
            List<RuleGroup> ruleGroups = ruleDictService.getAllRuleGroups();
            ruleGroups.forEach(group -> {
                ruleGroupCache.put(group.getId(), group);
                if (group.getProjectId() != null) {
                    ruleGroupByProjectCache.computeIfAbsent(group.getProjectId(), k -> new ArrayList<>())
                            .add(group);
                }
            });

            // 加载规则表达式
            ruleGroups.forEach(group -> {
                List<RuleExpression> expressions = ruleDictService.getExpressionsByGroupId(group.getId());
                if (expressions != null) {
                    expressions.forEach(exp -> expressionCache.put(exp.getId(), exp));
                    expressionByGroupIdCache.put(group.getId(), expressions);
                }
            });

            log.info("缓存加载完成 - 规则流: {}, 规则: {}, 字段: {}, 字段类型: {}, 运算符: {}, 规则组: {}, 表达式: {}",
                    ruleFlowCache.size(), ruleCache.size(), fieldCache.size(),
                    fieldTypeCache.size(), operatorCache.size(), ruleGroupCache.size(), expressionCache.size());

        } catch (Exception e) {
            log.error("加载缓存失败", e);
        }
    }

    // ==================== 缓存查询方法 ====================

    public RuleFlowDict getRuleFlowById(Long id) {
        return ruleFlowCache.get(id);
    }

    public RuleDict getRuleById(Long id) {
        return ruleCache.get(id);
    }

    public RuleDict getRuleByQueryKey(String queryKey) {
        return ruleByQueryKeyCache.get(queryKey);
    }

    public RuleFieldDict getFieldById(Long id) {
        return fieldCache.get(id);
    }

    public List<RuleFieldDict> getFieldsByRuleId(Long ruleId) {
        return fieldByRuleIdCache.getOrDefault(ruleId, Collections.emptyList());
    }

    public RuleFieldType getFieldTypeById(Long id) {
        return fieldTypeCache.get(id);
    }

    public RuleFieldType getFieldTypeByCode(String code) {
        return fieldTypeByCodeCache.get(code != null ? code.toUpperCase() : null);
    }

    public RuleOperator getOperatorById(Long id) {
        return operatorCache.get(id);
    }

    public RuleOperator getOperatorByCode(String code) {
        return operatorByCodeCache.get(code);
    }

    public RuleGroup getRuleGroupById(Long id) {
        return ruleGroupCache.get(id);
    }

    public List<RuleGroup> getRuleGroupsByProjectId(String projectId) {
        return ruleGroupByProjectCache.getOrDefault(projectId, Collections.emptyList());
    }

    public RuleExpression getExpressionById(Long id) {
        return expressionCache.get(id);
    }

    public List<RuleExpression> getExpressionsByGroupId(Long groupId) {
        return expressionByGroupIdCache.getOrDefault(groupId, Collections.emptyList());
    }

    /**
     * 更新规则组缓存
     */
    public void updateRuleGroupCache(RuleGroup group) {
        if (group == null) return;
        ruleGroupCache.put(group.getId(), group);
        if (group.getProjectId() != null) {
            ruleGroupByProjectCache.computeIfAbsent(group.getProjectId(), k -> new ArrayList<>())
                    .add(group);
        }
    }

    /**
     * 更新表达式缓存
     */
    public void updateExpressionCache(RuleExpression expression) {
        if (expression == null) return;
        expressionCache.put(expression.getId(), expression);
        expressionByGroupIdCache.computeIfAbsent(expression.getRuleGroupId(), k -> new ArrayList<>())
                .add(expression);
    }

    /**
     * 移除规则组缓存
     */
    public void removeRuleGroupCache(Long id) {
        RuleGroup group = ruleGroupCache.remove(id);
        if (group != null && group.getProjectId() != null) {
            List<RuleGroup> groups = ruleGroupByProjectCache.get(group.getProjectId());
            if (groups != null) {
                groups.removeIf(g -> g.getId().equals(id));
            }
        }
        expressionByGroupIdCache.remove(id);
    }
}

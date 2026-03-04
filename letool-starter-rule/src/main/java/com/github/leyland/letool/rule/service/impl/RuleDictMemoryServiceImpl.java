package com.github.leyland.letool.rule.service.impl;

import com.github.leyland.letool.rule.entity.*;
import com.github.leyland.letool.rule.service.RuleDictService;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 内存字典服务实现
 * 提供开箱即用的内存存储实现
 *
 * @author leyland
 * @since 2026/02/14
 */
@Slf4j
public class RuleDictMemoryServiceImpl implements RuleDictService {

    // 存储容器
    private final Map<Long, RuleFlowDict> ruleFlowStore = new ConcurrentHashMap<>();
    private final Map<Long, RuleDict> ruleStore = new ConcurrentHashMap<>();
    private final Map<Long, RuleFieldDict> fieldStore = new ConcurrentHashMap<>();
    private final Map<Long, RuleFieldType> fieldTypeStore = new ConcurrentHashMap<>();
    private final Map<Long, RuleOperator> operatorStore = new ConcurrentHashMap<>();
    private final Map<Long, RuleGroup> ruleGroupStore = new ConcurrentHashMap<>();
    private final Map<Long, RuleExpression> expressionStore = new ConcurrentHashMap<>();

    // ID生成器
    private final AtomicLong idGenerator = new AtomicLong(1);

    public RuleDictMemoryServiceImpl() {
        log.info("初始化内存字典服务，加载默认数据...");
        loadDefaultData();
    }

    /**
     * 加载默认数据
     */
    private void loadDefaultData() {
        // 加载默认字段类型
        initDefaultFieldTypes();

        // 加载默认运算符
        initDefaultOperators();

        log.info("默认数据加载完成 - 字段类型: {}, 运算符: {}",
                fieldTypeStore.size(), operatorStore.size());
    }

    /**
     * 初始化默认字段类型
     */
    private void initDefaultFieldTypes() {
        String[][] types = {
                {"1", "STRING", "字符串", "用于存储文本信息"},
                {"2", "NUMBER", "数字", "用于存储数值信息"},
                {"3", "DATE", "日期", "用于存储日期信息", "yyyy-MM-dd"},
                {"4", "DATETIME", "日期时间", "用于存储日期时间信息", "yyyy-MM-dd HH:mm:ss"},
                {"5", "BOOLEAN", "布尔", "用于存储布尔值"}
        };

        for (String[] type : types) {
            RuleFieldType fieldType = new RuleFieldType();
            fieldType.setId(Long.parseLong(type[0]));
            fieldType.setTypeCode(type[1]);
            fieldType.setTypeName(type[2]);
            fieldType.setTypeDescription(type[3]);
            if (type.length > 4) {
                fieldType.setDateFormat(type[4]);
            }
            fieldType.setEnabled(true);
            fieldType.setSortOrder(Integer.parseInt(type[0]));
            fieldTypeStore.put(fieldType.getId(), fieldType);
        }
    }

    /**
     * 初始化默认运算符
     */
    private void initDefaultOperators() {
        String[][] operators = {
                {"1", "等于", "EQ", "=", "判断两个值是否相等", "STRING,NUMBER,DATE,BOOLEAN,DATETIME", "true"},
                {"2", "不等于", "NEQ", "!=", "判断两个值是否不相等", "STRING,NUMBER,DATE,BOOLEAN,DATETIME", "true"},
                {"3", "大于", "GT", ">", "判断左边值是否大于右边值", "NUMBER,DATE,DATETIME", "true"},
                {"4", "大于等于", "GTE", ">=", "判断左边值是否大于等于右边值", "NUMBER,DATE,DATETIME", "true"},
                {"5", "小于", "LT", "<", "判断左边值是否小于右边值", "NUMBER,DATE,DATETIME", "true"},
                {"6", "小于等于", "LTE", "<=", "判断左边值是否小于等于右边值", "NUMBER,DATE,DATETIME", "true"},
                {"7", "模糊匹配", "LIKE", "LIKE", "模糊匹配（支持%和_通配符）", "STRING", "true"},
                {"8", "包含于", "IN", "IN", "判断值是否在列表中", "STRING,NUMBER,DATE,DATETIME", "true"},
                {"9", "不包含于", "NOT_IN", "NOT IN", "判断值是否不在列表中", "STRING,NUMBER,DATE,DATETIME", "true"},
                {"10", "为空", "IS_NULL", "IS NULL", "判断值是否为空", "STRING,NUMBER,DATE,BOOLEAN,DATETIME", "false"},
                {"11", "不为空", "IS_NOT_NULL", "IS NOT NULL", "判断值是否不为空", "STRING,NUMBER,DATE,BOOLEAN,DATETIME", "false"}
        };

        for (String[] op : operators) {
            RuleOperator operator = new RuleOperator();
            operator.setId(Long.parseLong(op[0]));
            operator.setOperatorName(op[1]);
            operator.setOperatorCode(op[2]);
            operator.setOperatorSymbol(op[3]);
            operator.setOperatorDescription(op[4]);
            operator.setSupportedFieldTypes(op[5]);
            operator.setRequiresValue(Boolean.parseBoolean(op[6]));
            operator.setEnabled(true);
            operator.setSortOrder(Integer.parseInt(op[0]));
            operatorStore.put(operator.getId(), operator);
        }
    }

    private Long generateId() {
        return idGenerator.getAndIncrement();
    }

    // ==================== 规则流相关 ====================

    @Override
    public List<RuleFlowDict> getAllRuleFlows() {
        return new ArrayList<>(ruleFlowStore.values());
    }

    @Override
    public RuleFlowDict getRuleFlowById(Long id) {
        return ruleFlowStore.get(id);
    }

    // ==================== 规则字典相关 ====================

    @Override
    public List<RuleDict> getAllRules() {
        return new ArrayList<>(ruleStore.values());
    }

    @Override
    public RuleDict getRuleById(Long id) {
        return ruleStore.get(id);
    }

    @Override
    public RuleDict getRuleByQueryKey(String queryKey) {
        return ruleStore.values().stream()
                .filter(r -> queryKey.equals(r.getQueryKey()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<RuleDict> getRulesByFlowId(Long flowId) {
        return ruleStore.values().stream()
                .filter(r -> flowId.equals(r.getRuleFlowId()))
                .collect(Collectors.toList());
    }

    // ==================== 字段相关 ====================

    @Override
    public List<RuleFieldDict> getAllFields() {
        return new ArrayList<>(fieldStore.values());
    }

    @Override
    public RuleFieldDict getFieldById(Long id) {
        return fieldStore.get(id);
    }

    @Override
    public List<RuleFieldDict> getFieldsByRuleId(Long ruleId) {
        return fieldStore.values().stream()
                .filter(f -> ruleId.equals(f.getRuleId()))
                .collect(Collectors.toList());
    }

    // ==================== 字段类型相关 ====================

    @Override
    public List<RuleFieldType> getAllFieldTypes() {
        return new ArrayList<>(fieldTypeStore.values());
    }

    @Override
    public RuleFieldType getFieldTypeById(Long id) {
        return fieldTypeStore.get(id);
    }

    @Override
    public RuleFieldType getFieldTypeByCode(String typeCode) {
        return fieldTypeStore.values().stream()
                .filter(t -> typeCode.equalsIgnoreCase(t.getTypeCode()))
                .findFirst()
                .orElse(null);
    }

    // ==================== 运算符相关 ====================

    @Override
    public List<RuleOperator> getAllOperators() {
        return new ArrayList<>(operatorStore.values());
    }

    @Override
    public RuleOperator getOperatorById(Long id) {
        return operatorStore.get(id);
    }

    @Override
    public RuleOperator getOperatorByCode(String code) {
        return operatorStore.values().stream()
                .filter(o -> code.equals(o.getOperatorCode()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<RuleOperator> getOperatorsByFieldTypeId(Long fieldTypeId) {
        RuleFieldType fieldType = getFieldTypeById(fieldTypeId);
        if (fieldType == null || fieldType.getTypeCode() == null) {
            return Collections.emptyList();
        }

        return operatorStore.values().stream()
                .filter(op -> {
                    String supportedTypes = op.getSupportedFieldTypes();
                    if (supportedTypes == null) return false;
                    return Arrays.asList(supportedTypes.split(","))
                            .contains(fieldType.getTypeCode());
                })
                .collect(Collectors.toList());
    }

    // ==================== 规则组相关 ====================

    @Override
    public List<RuleGroup> getAllRuleGroups() {
        return new ArrayList<>(ruleGroupStore.values());
    }

    @Override
    public RuleGroup getRuleGroupById(Long id) {
        return ruleGroupStore.get(id);
    }

    @Override
    public List<RuleGroup> getRuleGroupsByProjectId(String projectId) {
        return ruleGroupStore.values().stream()
                .filter(g -> projectId.equals(g.getProjectId()))
                .collect(Collectors.toList());
    }

    @Override
    public RuleGroup saveRuleGroup(RuleGroup ruleGroup) {
        if (ruleGroup.getId() == null) {
            ruleGroup.setId(generateId());
        }
        ruleGroup.setCreateTime(new Date());
        ruleGroup.setUpdateTime(new Date());
        ruleGroupStore.put(ruleGroup.getId(), ruleGroup);
        return ruleGroup;
    }

    @Override
    public void deleteRuleGroup(Long id) {
        ruleGroupStore.remove(id);
        // 删除关联的表达式
        expressionStore.entrySet().removeIf(e -> id.equals(e.getValue().getRuleGroupId()));
    }

    // ==================== 规则表达式相关 ====================

    @Override
    public RuleExpression getExpressionById(Long id) {
        return expressionStore.get(id);
    }

    @Override
    public List<RuleExpression> getExpressionsByGroupId(Long groupId) {
        return expressionStore.values().stream()
                .filter(e -> groupId.equals(e.getRuleGroupId()))
                .sorted(Comparator.comparing(RuleExpression::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    @Override
    public List<RuleExpression> getExpressionsByGroupIdAndType(Long groupId, Integer ruleType) {
        return expressionStore.values().stream()
                .filter(e -> groupId.equals(e.getRuleGroupId()) && ruleType.equals(e.getRuleType()))
                .sorted(Comparator.comparing(RuleExpression::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    @Override
    public List<RuleExpression> getExpressionsByProjectId(String projectId) {
        List<Long> groupIds = ruleGroupStore.values().stream()
                .filter(g -> projectId.equals(g.getProjectId()))
                .map(RuleGroup::getId)
                .collect(Collectors.toList());

        return expressionStore.values().stream()
                .filter(e -> groupIds.contains(e.getRuleGroupId()))
                .collect(Collectors.toList());
    }

    @Override
    public RuleExpression saveExpression(RuleExpression expression) {
        if (expression.getId() == null) {
            expression.setId(generateId());
        }
        expression.setCreateTime(new Date());
        expression.setUpdateTime(new Date());
        expressionStore.put(expression.getId(), expression);
        return expression;
    }

    @Override
    public void saveExpressions(List<RuleExpression> expressions) {
        expressions.forEach(this::saveExpression);
    }

    @Override
    public void deleteExpression(Long id) {
        expressionStore.remove(id);
    }

    // ==================== 缓存相关 ====================

    @Override
    public void refreshCache() {
        log.info("内存存储无需刷新缓存");
    }

    @Override
    public void refreshCache(String cacheType) {
        log.info("内存存储无需刷新缓存: {}", cacheType);
    }
}

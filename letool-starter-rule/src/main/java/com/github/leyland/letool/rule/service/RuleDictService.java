package com.github.leyland.letool.rule.service;

import com.github.leyland.letool.rule.entity.*;

import java.util.List;
import java.util.Map;

/**
 * 规则字典服务接口
 * 提供字典数据的查询和管理功能
 *
 * @author leyland
 * @since 2026/02/14
 */
public interface RuleDictService {

    // ==================== 规则流（分类）相关 ====================

    /**
     * 获取所有规则流
     */
    List<RuleFlowDict> getAllRuleFlows();

    /**
     * 根据ID获取规则流
     */
    RuleFlowDict getRuleFlowById(Long id);

    // ==================== 规则（表名）相关 ====================

    /**
     * 获取所有规则字典
     */
    List<RuleDict> getAllRules();

    /**
     * 根据ID获取规则字典
     */
    RuleDict getRuleById(Long id);

    /**
     * 根据查询键获取规则字典
     */
    RuleDict getRuleByQueryKey(String queryKey);

    /**
     * 根据规则流ID获取规则列表
     */
    List<RuleDict> getRulesByFlowId(Long flowId);

    // ==================== 字段相关 ====================

    /**
     * 获取所有字段字典
     */
    List<RuleFieldDict> getAllFields();

    /**
     * 根据ID获取字段字典
     */
    RuleFieldDict getFieldById(Long id);

    /**
     * 根据规则ID获取字段列表
     */
    List<RuleFieldDict> getFieldsByRuleId(Long ruleId);

    // ==================== 字段类型相关 ====================

    /**
     * 获取所有字段类型
     */
    List<RuleFieldType> getAllFieldTypes();

    /**
     * 根据ID获取字段类型
     */
    RuleFieldType getFieldTypeById(Long id);

    /**
     * 根据类型编码获取字段类型
     */
    RuleFieldType getFieldTypeByCode(String typeCode);

    // ==================== 运算符相关 ====================

    /**
     * 获取所有运算符
     */
    List<RuleOperator> getAllOperators();

    /**
     * 根据ID获取运算符
     */
    RuleOperator getOperatorById(Long id);

    /**
     * 根据运算符编码获取运算符
     */
    RuleOperator getOperatorByCode(String code);

    /**
     * 根据字段类型ID获取支持的运算符
     */
    List<RuleOperator> getOperatorsByFieldTypeId(Long fieldTypeId);

    // ==================== 规则组相关 ====================

    /**
     * 获取所有规则组
     */
    List<RuleGroup> getAllRuleGroups();

    /**
     * 根据ID获取规则组
     */
    RuleGroup getRuleGroupById(Long id);

    /**
     * 根据项目ID获取规则组
     */
    List<RuleGroup> getRuleGroupsByProjectId(String projectId);

    /**
     * 保存规则组
     */
    RuleGroup saveRuleGroup(RuleGroup ruleGroup);

    /**
     * 删除规则组
     */
    void deleteRuleGroup(Long id);

    // ==================== 规则表达式相关 ====================

    /**
     * 根据ID获取规则表达式
     */
    RuleExpression getExpressionById(Long id);

    /**
     * 根据规则组ID获取表达式列表
     */
    List<RuleExpression> getExpressionsByGroupId(Long groupId);

    /**
     * 根据规则组ID和规则类型获取表达式列表
     */
    List<RuleExpression> getExpressionsByGroupIdAndType(Long groupId, Integer ruleType);

    /**
     * 根据项目ID获取所有表达式
     */
    List<RuleExpression> getExpressionsByProjectId(String projectId);

    /**
     * 保存规则表达式
     */
    RuleExpression saveExpression(RuleExpression expression);

    /**
     * 批量保存规则表达式
     */
    void saveExpressions(List<RuleExpression> expressions);

    /**
     * 删除规则表达式
     */
    void deleteExpression(Long id);

    // ==================== 缓存相关 ====================

    /**
     * 刷新所有缓存
     */
    void refreshCache();

    /**
     * 刷新指定类型缓存
     */
    void refreshCache(String cacheType);
}

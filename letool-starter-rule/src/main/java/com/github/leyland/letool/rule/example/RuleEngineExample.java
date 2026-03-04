package com.github.leyland.letool.rule.example;

import com.github.leyland.letool.rule.entity.*;
import com.github.leyland.letool.rule.model.*;
import com.github.leyland.letool.rule.service.RuleDictService;
import com.github.leyland.letool.rule.service.RuleValidationService;

import java.util.*;

/**
 * 规则引擎使用示例
 *
 * @author leyland
 * @since 2026/02/14
 */
public class RuleEngineExample {

    private RuleDictService dictService;
    private RuleValidationService validationService;

    /**
     * 示例1：创建规则组和规则表达式
     */
    public void example1_CreateRuleGroup() {
        // 1. 创建规则字典（表名）
        RuleDict ruleDict = new RuleDict();
        ruleDict.setRuleName("患者基本信息");
        ruleDict.setQueryKey("patient_base_info");
        ruleDict.setTableName("patient_base_info");
        // dictService.saveRule(ruleDict);

        // 2. 创建字段字典
        RuleFieldDict ageField = new RuleFieldDict();
        ageField.setRuleId(ruleDict.getId());
        ageField.setFieldName("年龄");
        ageField.setDatabaseFieldCode("age");
        ageField.setFieldTypeId(2L); // NUMBER类型
        // dictService.saveField(ageField);

        RuleFieldDict genderField = new RuleFieldDict();
        genderField.setRuleId(ruleDict.getId());
        genderField.setFieldName("性别");
        genderField.setDatabaseFieldCode("gender");
        genderField.setFieldTypeId(1L); // STRING类型
        // dictService.saveField(genderField);

        // 3. 创建规则组
        RuleGroup ruleGroup = new RuleGroup();
        ruleGroup.setName("年龄筛选规则");
        ruleGroup.setProjectId("PROJECT_001");
        ruleGroup.setGroupType(0); // 纳入规则组
        ruleGroup.setStopOnFailure(1); // 且关系
        ruleGroup.setEnabled(true);
        ruleGroup.setSortOrder(1);
        ruleGroup = dictService.saveRuleGroup(ruleGroup);

        // 4. 创建规则表达式
        RuleExpression expression1 = new RuleExpression();
        expression1.setRuleGroupId(ruleGroup.getId());
        expression1.setRuleId(ruleDict.getId());
        expression1.setRuleFieldId(ageField.getId());
        expression1.setOperatorId(5L); // LT 小于
        expression1.setCompareValue("65");
        expression1.setRuleType(0); // 纳入规则
        expression1.setStopOnFailure(1); // 且关系
        expression1.setSortOrder(1);
        expression1.setDescription("年龄小于65岁");
        dictService.saveExpression(expression1);

        RuleExpression expression2 = new RuleExpression();
        expression2.setRuleGroupId(ruleGroup.getId());
        expression2.setRuleId(ruleDict.getId());
        expression2.setRuleFieldId(genderField.getId());
        expression2.setOperatorId(1L); // EQ 等于
        expression2.setCompareValue("男");
        expression2.setRuleType(0);
        expression2.setStopOnFailure(0); // 或关系
        expression2.setSortOrder(2);
        expression2.setDescription("性别为男");
        dictService.saveExpression(expression2);
    }

    /**
     * 示例2：单条数据校验
     */
    public void example2_ValidateSingle() {
        // 准备测试数据
        Map<String, Object> data = new HashMap<>();
        data.put("age", 45);
        data.put("gender", "男");
        data.put("name", "张三");
        data.put("id_no", "110101199001011234");

        // 执行验证（指定项目ID）
        RuleValidationResult result = validationService.validateSingle("PROJECT_001", data);

        // 查看结果
        System.out.println("验证结果: " + (result.isPassed() ? "通过" : "不通过"));
        System.out.println("是否被排除: " + result.isExcluded());
        System.out.println("执行时间: " + result.getExecutionTime() + "ms");

        // 查看详细结果
        for (RuleValidationResult.RuleGroupValidationResult groupResult : result.getGroupResults()) {
            System.out.println("规则组: " + groupResult.getRuleGroupName());
            System.out.println("  结果: " + (groupResult.isPassed() ? "通过" : "不通过"));

            for (RuleValidationResult.RuleExpressionResult expResult : groupResult.getExpressionResults()) {
                System.out.println("  - " + expResult.getFieldName() + " " + expResult.getOperatorSymbol() + " " + expResult.getCompareValue());
                System.out.println("    实际值: " + expResult.getActualValue());
                System.out.println("    匹配: " + (expResult.isMatched() ? "是" : "否"));
            }
        }
    }

    /**
     * 示例3：批量数据校验
     */
    public void example3_ValidateBatch() {
        // 准备批量数据
        List<Map<String, Object>> dataList = new ArrayList<>();

        Map<String, Object> data1 = new HashMap<>();
        data1.put("age", 45);
        data1.put("gender", "男");
        data1.put("patient_id", "P001");
        dataList.add(data1);

        Map<String, Object> data2 = new HashMap<>();
        data2.put("age", 70);
        data2.put("gender", "女");
        data2.put("patient_id", "P002");
        dataList.add(data2);

        // 构建请求
        RuleValidationRequest request = new RuleValidationRequest();
        request.setProjectId("PROJECT_001");
        request.setBatchData(dataList);
        request.setExecuteMode(RuleValidationRequest.ExecuteMode.PARALLEL); // 并行执行
        request.setReturnDetails(true);

        // 执行验证
        BatchValidationResult result = validationService.validateBatch(request);

        // 查看汇总结果
        System.out.println("批次ID: " + result.getBatchId());
        System.out.println("总数: " + result.getTotalCount());
        System.out.println("通过: " + result.getPassedCount());
        System.out.println("失败: " + result.getFailedCount());
        System.out.println("排除: " + result.getExcludedCount());
        System.out.println("通过率: " + result.getPassRate() + "%");
        System.out.println("总耗时: " + result.getExecutionTime() + "ms");
    }

    /**
     * 示例4：排除规则组
     */
    public void example4_ExcludeRuleGroup() {
        // 创建排除规则组
        RuleGroup excludeGroup = new RuleGroup();
        excludeGroup.setName("排除条件");
        excludeGroup.setProjectId("PROJECT_001");
        excludeGroup.setGroupType(1); // 排除规则组
        excludeGroup.setStopOnFailure(1);
        excludeGroup.setEnabled(true);
        excludeGroup = dictService.saveRuleGroup(excludeGroup);

        // 添加排除规则（如：排除有重大疾病史的患者）
        RuleExpression excludeExpression = new RuleExpression();
        excludeExpression.setRuleGroupId(excludeGroup.getId());
        // ... 设置其他字段
        excludeExpression.setRuleType(1); // 排除规则
        dictService.saveExpression(excludeExpression);
    }

    /**
     * 示例5：通过API调用
     * 
     * POST /api/rule/validate/single
     * Content-Type: application/json
     * 
     * {
     *   "projectId": "PROJECT_001",
     *   "singleData": {
     *     "age": 45,
     *     "gender": "男",
     *     "name": "张三"
     *   }
     * }
     * 
     * POST /api/rule/validate/batch
     * Content-Type: application/json
     * 
     * {
     *   "projectId": "PROJECT_001",
     *   "batchData": [
     *     {"age": 45, "gender": "男", "patient_id": "P001"},
     *     {"age": 70, "gender": "女", "patient_id": "P002"}
     *   ],
     *   "executeMode": "PARALLEL"
     * }
     */
    public void example5_ApiCall() {
        // 参考 controller 包中的 RuleValidationController
        // 提供 REST API 接口
    }
}

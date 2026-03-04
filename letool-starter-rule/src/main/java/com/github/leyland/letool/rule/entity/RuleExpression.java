package com.github.leyland.letool.rule.entity;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 规则表达式实体
 * 定义具体的规则条件
 *
 * @author leyland
 * @since 2026/02/14
 */
@Data
public class RuleExpression implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 所属规则组ID
     */
    private Long ruleGroupId;

    /**
     * 规则流ID（分类）
     */
    private Long ruleFlowId;

    /**
     * 规则ID（关联表名）
     */
    private Long ruleId;

    /**
     * 规则字段ID
     */
    private Long ruleFieldId;

    /**
     * 运算符ID
     */
    private Long operatorId;

    /**
     * 比较值
     */
    private Object compareValue;

    /**
     * 规则类型
     * 0 - 纳入规则
     * 1 - 排除规则
     */
    private Integer ruleType;

    /**
     * 表达式描述
     */
    private String description;

    /**
     * 执行顺序
     */
    private Integer sortOrder;

    /**
     * 失败时是否停止（组内规则间的关系）
     * 0 - 或关系
     * 1 - 且关系
     */
    private Integer stopOnFailure;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;
}

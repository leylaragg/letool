package com.github.leyland.letool.rule.entity;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 规则组实体
 * 每个项目下可以有N个规则组，包含纳入规则组和排除规则组
 *
 * @author leyland
 * @since 2026/02/14
 */
@Data
public class RuleGroup implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 规则组名称
     */
    private String name;

    /**
     * 规则组描述
     */
    private String description;

    /**
     * 项目ID（业务关联）
     */
    private String projectId;

    /**
     * 规则组序列号（执行顺序）
     */
    private Integer sortOrder;

    /**
     * 失败时是否停止
     * 0 - 或关系（组间任意一个通过即可）
     * 1 - 且关系（组间所有必须通过）
     */
    private Integer stopOnFailure;

    /**
     * 规则组类型
     * 0 - 纳入规则组
     * 1 - 排除规则组
     */
    private Integer groupType;

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

    /**
     * 规则表达式列表（非持久化字段）
     */
    private transient List<RuleExpression> ruleExpressions;
}

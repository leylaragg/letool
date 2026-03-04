package com.github.leyland.letool.rule.entity;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 规则字典实体（表名字典）
 * 定义规则对应的数据库表或数据源
 *
 * @author leyland
 * @since 2026/02/14
 */
@Data
public class RuleDict implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 规则名称
     */
    private String ruleName;

    /**
     * 查询键（对应数据源中的表或查询标识）
     */
    private String queryKey;

    /**
     * 表名称（数据库表名）
     */
    private String tableName;

    /**
     * 表描述
     */
    private String tableDescription;

    /**
     * 规则流ID（分类）
     */
    private Long ruleFlowId;

    /**
     * 排序
     */
    private Integer sortOrder;

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

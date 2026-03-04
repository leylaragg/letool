package com.github.leyland.letool.rule.entity;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 规则流字典实体（分类字典）
 * 用于对规则进行分类管理
 *
 * @author leyland
 * @since 2026/02/14
 */
@Data
public class RuleFlowDict implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 规则流名称
     */
    private String flowName;

    /**
     * 规则流编码
     */
    private String flowCode;

    /**
     * 规则流描述
     */
    private String flowDescription;

    /**
     * 父级ID（支持层级结构）
     */
    private Long parentId;

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

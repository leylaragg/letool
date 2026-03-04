package com.github.leyland.letool.rule.entity;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 字段-运算符关联实体
 * 定义哪些字段支持哪些运算符
 *
 * @author leyland
 * @since 2026/02/14
 */
@Data
public class RuleFieldOperator implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 字段类型ID
     */
    private Long fieldTypeId;

    /**
     * 运算符ID
     */
    private Long operatorId;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 创建时间
     */
    private Date createTime;
}

package com.github.leyland.letool.rule.entity;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 运算符字典实体
 * 定义支持的运算操作
 *
 * @author leyland
 * @since 2026/02/14
 */
@Data
public class RuleOperator implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 运算符名称
     */
    private String operatorName;

    /**
     * 运算符编码（EQ, NEQ, GT, GTE, LT, LTE, LIKE, IN, NOT_IN, IS_NULL, IS_NOT_NULL）
     */
    private String operatorCode;

    /**
     * 运算符符号（=, !=, >, >=, <, <=, LIKE, IN, NOT IN, IS NULL, IS NOT NULL）
     */
    private String operatorSymbol;

    /**
     * 运算符描述
     */
    private String operatorDescription;

    /**
     * 支持的字段类型（逗号分隔，如：STRING,NUMBER,DATE）
     */
    private String supportedFieldTypes;

    /**
     * 是否需要比较值
     */
    private Boolean requiresValue;

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

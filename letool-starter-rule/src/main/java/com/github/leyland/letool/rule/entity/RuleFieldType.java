package com.github.leyland.letool.rule.entity;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 规则字段类型实体
 * 定义字段的数据类型
 *
 * @author leyland
 * @since 2026/02/14
 */
@Data
public class RuleFieldType implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 类型编码（STRING, NUMBER, DATE, BOOLEAN, JSON）
     */
    private String typeCode;

    /**
     * 类型名称
     */
    private String typeName;

    /**
     * 类型描述
     */
    private String typeDescription;

    /**
     * 日期格式（仅日期类型使用）
     */
    private String dateFormat;

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

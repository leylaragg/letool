package com.github.leyland.letool.rule.entity;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 规则字段字典实体
 * 定义规则中可使用的字段
 *
 * @author leyland
 * @since 2026/02/14
 */
@Data
public class RuleFieldDict implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 所属规则ID（表名）
     */
    private Long ruleId;

    /**
     * 字段名称（显示名称）
     */
    private String fieldName;

    /**
     * 数据库字段编码
     */
    private String databaseFieldCode;

    /**
     * 字段类型ID
     */
    private Long fieldTypeId;

    /**
     * 字段描述
     */
    private String fieldDescription;

    /**
     * 默认值
     */
    private String defaultValue;

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

package com.github.leyland.letool.rule.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则引擎配置属性
 *
 * @author leyland
 * @since 2026/02/14
 */
@Data
@ConfigurationProperties(prefix = "letool.rule")
public class RuleEngineProperties {

    /**
     * 是否启用规则引擎
     */
    private Boolean enabled = true;

    /**
     * 是否启用缓存
     */
    private Boolean cacheEnabled = true;

    /**
     * 是否启用Web接口
     */
    private Boolean webEnabled = true;

    /**
     * 并行执行线程数
     */
    private int parallelThreads = 4;

    /**
     * 批量执行默认批次大小
     */
    private int defaultBatchSize = 1000;

    /**
     * 数据库筛选配置
     */
    private DatabaseValidationConfig databaseValidation = new DatabaseValidationConfig();

    /**
     * 初始字典数据配置
     */
    private List<DictConfig> dictConfigs = new ArrayList<>();

    /**
     * 数据库筛选配置
     */
    @Data
    public static class DatabaseValidationConfig {
        /**
         * 是否启用数据库筛选
         */
        private Boolean enabled = false;

        /**
         * 默认数据源名称
         */
        private String defaultDatasource;

        /**
         * 基本信息表查询键
         */
        private String baseInfoQueryKey = "patient_base_info";

        /**
         * 每批次读取数量
         */
        private int batchSize = 1000;
    }

    /**
     * 字典配置
     */
    @Data
    public static class DictConfig {
        /**
         * 字典类型
         * OPERATOR - 运算符
         * FIELD_TYPE - 字段类型
         */
        private String type;

        /**
         * 是否加载默认数据
         */
        private Boolean loadDefault = true;
    }
}

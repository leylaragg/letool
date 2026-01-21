package com.github.leyland.letool.data.database.core;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * @ClassName <h2>DatabaseConfig</h2>
 * @Description     数据库配置属性类
 *                      用于映射application.yml中的配置
 * @Author leyland
 * @Date 2026/01/16
 * @Version 1.0
 **/
@Data
@ConfigurationProperties(prefix = "letool.database")
public class DatabaseConfig {

    /**
     * 是否启用数据库模块
     */
    private Boolean enabled = false;

    /**
     * 是否使用 Spring Boot 默认数据源
     */
    private Boolean useSpringDataSource = false;

    /**
     * 外部数据源连接配置
     * key: 数据源名称, value: 数据源连接参数
     */
    private Map<String, DataSourceProperties> datasources;

    /**
     * 数据库查询配置列表
     */
    private List<QueryConfig> queries;

    /**
     * 数据源连接属性配置
     */
    @Data
    public static class DataSourceProperties {
        /**
         * 数据库连接URL
         */
        private String url;

        /**
         * 数据库用户名
         */
        private String username;

        /**
         * 数据库密码
         */
        private String password;

        /**
         * JDBC驱动类名
         */
        private String driverClassName;

        /**
         * 连接池类型：HikariCP, Druid
         */
        private String poolType = "HikariCP";

        /**
         * 初始化连接数
         */
        private Integer initialSize = 5;

        /**
         * 最小空闲连接数
         */
        private Integer minIdle = 5;

        /**
         * 最大活跃连接数
         */
        private Integer maxActive = 20;

        /**
         * 获取连接最大等待时间(毫秒)
         */
        private Long maxWait = 60000L;

        /**
         * 连接有效性检查的SQL
         */
        private String validationQuery = "SELECT 1";
    }

    /**
     * 数据库查询配置
     */
    @Data
    public static class QueryConfig {
        /**
         * 查询标识key，用于业务调用和字典映射
         */
        private String queryKey;

        /**
         * 数据库表名
         */
        private String tableName;

        /**
         * 使用的数据源名称（如果为空且 useSpringDataSource=true，则使用 Spring Boot 默认数据源）
         */
        private String datasource;

        /**
         * 主键字段名，用于分页查询
         */
        private String primaryKey = "id";

        /**
         * 每批次读取的数据量
         */
        private Integer batchSize = 1000;

        /**
         * 需要查询的字段列表（为空则查询所有字段）
         */
        private List<String> fields;

        /**
         * 查询条件(可选)
         */
        private String whereCondition;

        /**
         * 排序方式(可选)
         */
        private String orderBy;

        /**
         * 自定义SQL(优先级最高，如果配置了则忽略其他配置)
         */
        private String customSql;

        /**
         * 自定义SQL参数配置
         */
        private List<SqlParamConfig> sqlParams;

        /**
         * 是否使用流式查询
         */
        private Boolean streamQuery = false;
    }

    /**
     * SQL参数配置
     */
    @Data
    public static class SqlParamConfig {
        private String paramName;          // 参数名
        private String paramType;         // 参数类型：STRING, NUMBER, INTEGER, LONG, DATE, DATETIME, BOOLEAN
        private String defaultValue;      // 默认值
        private String valueSource;       // 值来源：STATIC, DYNAMIC, SYSTEM
        private Boolean required = false; // 是否必填
        private String format;           // 格式：如日期格式 "yyyy-MM-dd"

        // 验证规则
        private Integer minValue;        // 最小值（数字类型）
        private Integer maxValue;        // 最大值（数字类型）
        private Integer maxLength;       // 最大长度（字符串类型）
        private String pattern;          // 正则表达式验证
    }
}

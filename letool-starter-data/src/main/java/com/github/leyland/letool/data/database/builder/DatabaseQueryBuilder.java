package com.github.leyland.letool.data.database.builder;

import com.github.leyland.letool.data.database.core.DatabaseConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @ClassName <h2>DatabaseQueryBuilder</h2>
 * @Description     数据库SQL构建器
 *                      根据配置动态生成查询SQL
 * @Author leyland
 * @Date 2026/01/16
 * @Version 1.0
 **/
@Slf4j
public class DatabaseQueryBuilder {

    /**
     * WHERE条件解析结果
     */
    @Data
    @AllArgsConstructor
    private static class ParsedWhereCondition {
        private String condition;
        private Map<String, Object> params;
    }

    /**
     * SQL和参数的包装类
     */
    @Data
    @AllArgsConstructor
    public static class SqlWithParams {
        private String sql;
        private Map<String, Object> params;

        public boolean hasParams() {
            return params != null && !params.isEmpty();
        }
    }

    /**
     * 构建带参数的分页查询SQL
     */
    public SqlWithParams buildPaginationSQL(DatabaseConfig.QueryConfig queryConfig, Integer offset,
                                            Map<String, Object> dynamicParams) {
        if (StringUtils.hasText(queryConfig.getCustomSql())) {
            return buildParameterizedSQL(queryConfig.getCustomSql(),
                    queryConfig.getSqlParams(), dynamicParams, offset);
        }

        // 标准SQL构建（也支持参数）
        return buildStandardSQLWithParams(queryConfig, offset, dynamicParams);
    }

    /**
     * 构建参数化SQL
     */
    private SqlWithParams buildParameterizedSQL(String customSql,
                                                List<DatabaseConfig.SqlParamConfig> paramConfigs,
                                                Map<String, Object> dynamicParams,
                                                Integer offset) {
        Map<String, Object> actualParams = new HashMap<>();

        // 处理分页参数（如果SQL中包含分页占位符）
        if (offset != null && offset > 0) {
            if (customSql.contains(":offset") || customSql.contains("${offset}")) {
                actualParams.put("offset", offset);
            }
        }

        if (paramConfigs != null) {
            for (DatabaseConfig.SqlParamConfig paramConfig : paramConfigs) {
                Object paramValue = resolveParameterValue(paramConfig, dynamicParams);

                // 参数验证
                if (!validateParameter(paramConfig, paramValue)) {
                    throw new IllegalArgumentException("参数验证失败: " + paramConfig.getParamName());
                }

                if (paramValue != null) {
                    // 根据参数类型进行格式化
                    Object formattedValue = formatParameterValue(paramConfig, paramValue);
                    actualParams.put(paramConfig.getParamName(), formattedValue);

                    log.debug("参数处理: {} = {} (类型: {})", paramConfig.getParamName(), formattedValue, paramConfig.getParamType());
                } else if (paramConfig.getRequired()) {
                    throw new IllegalArgumentException("必填参数缺失: " + paramConfig.getParamName());
                }
            }
        }

        log.debug("生成的参数化SQL: {}", customSql);
        log.debug("SQL参数: {}", actualParams);

        return new SqlWithParams(customSql, actualParams);
    }

    /**
     * 构建标准SQL（支持参数）
     */
    private SqlWithParams buildStandardSQLWithParams(DatabaseConfig.QueryConfig config,
                                                     Integer offset, Map<String, Object> dynamicParams) {
        Map<String, Object> params = new HashMap<>();

        StringBuilder sql = new StringBuilder("SELECT ");

        // 处理查询字段
        if (config.getFields() != null && !config.getFields().isEmpty()) {
            sql.append(String.join(", ", config.getFields()));
        } else {
            sql.append("*");
        }

        sql.append(" FROM ").append(config.getTableName());

        // 构建WHERE条件（支持参数）
        List<String> conditions = new ArrayList<>();

        if (StringUtils.hasText(config.getWhereCondition())) {
            // 解析where条件中的参数
            ParsedWhereCondition parsed = parseWhereCondition(config.getWhereCondition(), dynamicParams);
            conditions.add(parsed.getCondition());
            params.putAll(parsed.getParams());
        }

        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }

        // ORDER BY
        sql.append(" ORDER BY ");
        if (StringUtils.hasText(config.getOrderBy())) {
            sql.append(config.getOrderBy());
        } else {
            // 默认根据id排序
            sql.append(config.getPrimaryKey()).append(" ASC");
        }

        // LIMIT（支持参数）
        sql.append(" LIMIT :batchSize");
        params.put("batchSize", config.getBatchSize());

        // OFFSET
        if (offset != null && offset > 0) {
            if (!Objects.equals(config.getBatchSize(), offset)) {
                // 设置偏移量
                sql.append(" OFFSET ").append(" :offset");
                params.put("offset", offset);
            }
        }

        return new SqlWithParams(sql.toString(), params);
    }

    /**
     * 解析WHERE条件中的参数
     */
    private ParsedWhereCondition parseWhereCondition(String whereCondition,
                                                     Map<String, Object> dynamicParams) {
        // 简单的参数解析逻辑，实际中可以更复杂
        Map<String, Object> params = new HashMap<>();

        // 示例：解析类似 "create_time > :startTime AND status = :status" 的条件
        if (dynamicParams != null) {
            for (Map.Entry<String, Object> entry : dynamicParams.entrySet()) {
                String paramName = entry.getKey();
                if (whereCondition.contains(":" + paramName)) {
                    params.put(paramName, entry.getValue());
                }
            }
        }

        return new ParsedWhereCondition(whereCondition, params);
    }

    /**
     * 解析参数值（支持多种数据类型）
     */
    private Object resolveParameterValue(DatabaseConfig.SqlParamConfig paramConfig,
                                         Map<String, Object> dynamicParams) {
        String paramType = paramConfig.getParamType() != null ?
                paramConfig.getParamType().toUpperCase() : "STRING";
        String valueSource = paramConfig.getValueSource() != null ?
                paramConfig.getValueSource().toUpperCase() : "STATIC";

        Object rawValue = null;

        switch (valueSource) {
            case "DYNAMIC":
                if (dynamicParams != null && dynamicParams.containsKey(paramConfig.getParamName())) {
                    rawValue = dynamicParams.get(paramConfig.getParamName());
                }
                break;

            case "SYSTEM":
                rawValue = resolveSystemParameter(paramConfig);
                break;

            case "STATIC":
            default:
                rawValue = paramConfig.getDefaultValue();
                break;
        }

        return convertToTargetType(rawValue, paramType, paramConfig.getFormat());
    }

    /**
     * 参数类型转换
     */
    private Object convertToTargetType(Object rawValue, String targetType, String format) {
        if (rawValue == null) return null;

        try {
            switch (targetType) {
                case "NUMBER":
                case "INTEGER":
                    if (rawValue instanceof Number) {
                        return rawValue;
                    }
                    return Integer.valueOf(rawValue.toString());

                case "LONG":
                    if (rawValue instanceof Long) {
                        return rawValue;
                    }
                    return Long.valueOf(rawValue.toString());

                case "DATE":
                    if (rawValue instanceof java.util.Date) {
                        return rawValue;
                    }
                    if (format != null) {
                        SimpleDateFormat sdf = new SimpleDateFormat(format);
                        return sdf.parse(rawValue.toString());
                    }
                    return java.sql.Date.valueOf(rawValue.toString());

                case "DATETIME":
                case "TIMESTAMP":
                    if (rawValue instanceof java.util.Date) {
                        return rawValue;
                    }
                    if (format != null) {
                        SimpleDateFormat sdf = new SimpleDateFormat(format);
                        return sdf.parse(rawValue.toString());
                    }
                    return java.sql.Timestamp.valueOf(rawValue.toString());

                case "BOOLEAN":
                    if (rawValue instanceof Boolean) {
                        return rawValue;
                    }
                    String strValue = rawValue.toString().toLowerCase();
                    return "true".equals(strValue) || "1".equals(strValue) || "yes".equals(strValue);

                case "STRING":
                default:
                    return rawValue.toString();
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("参数类型转换失败: " + rawValue + " -> " + targetType, e);
        }
    }

    /**
     * 格式化参数值（用于SQL查询）
     */
    private Object formatParameterValue(DatabaseConfig.SqlParamConfig paramConfig, Object paramValue) {
        if (paramValue == null) return null;

        String dataType = paramConfig.getParamType() != null ?
                paramConfig.getParamType().toUpperCase() : "STRING";

        try {
            switch (dataType) {
                case "INTEGER":
                case "LONG":
                case "NUMBER":
                    if (paramValue instanceof Number) {
                        return paramValue;
                    }
                    return paramValue.toString();

                case "DATE":
                    if (paramValue instanceof java.sql.Date) {
                        return paramValue;
                    }
                    if (paramValue instanceof java.util.Date) {
                        return new java.sql.Date(((java.util.Date) paramValue).getTime());
                    }
                    return java.sql.Date.valueOf(paramValue.toString());

                case "DATETIME":
                case "TIMESTAMP":
                    if (paramValue instanceof java.sql.Timestamp) {
                        return paramValue;
                    }
                    if (paramValue instanceof java.util.Date) {
                        return new java.sql.Timestamp(((java.util.Date) paramValue).getTime());
                    }
                    return java.sql.Timestamp.valueOf(paramValue.toString());

                case "BOOLEAN":
                    if (paramValue instanceof Boolean) {
                        return paramValue;
                    }
                    String strValue = paramValue.toString().toLowerCase();
                    return "true".equals(strValue) || "1".equals(strValue) || "yes".equals(strValue);

                case "STRING":
                default:
                    return paramValue.toString();
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("参数格式化失败: " + paramValue + " -> " + dataType, e);
        }
    }

    /**
     * 参数验证
     */
    private boolean validateParameter(DatabaseConfig.SqlParamConfig paramConfig, Object paramValue) {
        if (paramValue == null) {
            return !paramConfig.getRequired();
        }

        // 根据参数类型进行验证

        String paramType = paramConfig.getParamType() != null ? 
            paramConfig.getParamType().toUpperCase() : "STRING";
        
        switch (paramType) {
            case "STRING":
                return validateStringParameter(paramConfig, paramValue.toString());
            case "NUMBER":
            case "INTEGER":
            case "LONG":
            case "DECIMAL":
                return validateNumberParameter(paramConfig, paramValue);
            case "DATE":
            case "DATETIME":
                return validateDateParameter(paramConfig, paramValue);
            default:
                return true;
        }
    }

    private boolean validateStringParameter(DatabaseConfig.SqlParamConfig config, String value) {
        if (config.getMaxLength() != null && value.length() > config.getMaxLength()) {
            return false;
        }
        if (config.getPattern() != null && !value.matches(config.getPattern())) {
            return false;
        }
        return true;
    }

    private boolean validateNumberParameter(DatabaseConfig.SqlParamConfig config, Object value) {
        try {
            long numValue = Long.parseLong(value.toString());
            if (config.getMinValue() != null && numValue < config.getMinValue()) {
                return false;
            }
            return config.getMaxValue() == null || numValue <= config.getMaxValue();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean validateDateParameter(DatabaseConfig.SqlParamConfig config, Object value) {
        // 简单的日期验证，实际中可以更复杂
        return value instanceof java.util.Date;
    }

    /**
     * 解析系统参数
     */
    private Object resolveSystemParameter(DatabaseConfig.SqlParamConfig paramConfig) {
        String valueSource = paramConfig.getValueSource();
        if (valueSource == null) return null;


        String upperValueSource = valueSource.toUpperCase();
        if ("CURRENT_DATE".equals(upperValueSource)) {
            return java.sql.Date.valueOf(java.time.LocalDate.now());
        } else if ("CURRENT_DATETIME".equals(upperValueSource)) {
            return java.sql.Timestamp.valueOf(java.time.LocalDateTime.now());
        } else if ("CURRENT_TIMESTAMP".equals(upperValueSource)) {
            return System.currentTimeMillis();
        } else if ("CURRENT_YEAR".equals(upperValueSource)) {
            return java.time.LocalDate.now().getYear();
        } else if ("CURRENT_MONTH".equals(upperValueSource)) {
            return java.time.LocalDate.now().getMonthValue();
        } else if ("CURRENT_DAY".equals(upperValueSource)) {
            return java.time.LocalDate.now().getDayOfMonth();
        } else if ("YESTERDAY".equals(upperValueSource)) {
            return java.sql.Date.valueOf(java.time.LocalDate.now().minusDays(1));
        } else if ("TOMORROW".equals(upperValueSource)) {
            return java.sql.Date.valueOf(java.time.LocalDate.now().plusDays(1));
        } else if ("FIRST_DAY_OF_MONTH".equals(upperValueSource)) {
            return java.sql.Date.valueOf(java.time.LocalDate.now().withDayOfMonth(1));
        } else if ("LAST_DAY_OF_MONTH".equals(upperValueSource)) {
            return java.sql.Date.valueOf(java.time.LocalDate.now().with(java.time.temporal.TemporalAdjusters.lastDayOfMonth()));
        } else {
            return null;
        }
    }

    /**
     * 构建计数SQL（用于获取总数据量）
     */
    public String buildCountSQL(DatabaseConfig.QueryConfig queryConfig) {
        if (StringUtils.hasText(queryConfig.getCustomSql())) {
            // 从自定义SQL中提取计数SQL（简单实现）
            String customSql = queryConfig.getCustomSql().toLowerCase();
            int fromIndex = customSql.indexOf("from");
            if (fromIndex > 0) {
                return "SELECT COUNT(1) " + queryConfig.getCustomSql().substring(fromIndex);
            }
        }

        StringBuilder sql = new StringBuilder("SELECT COUNT(1) FROM ")
                .append(queryConfig.getTableName());

        if (StringUtils.hasText(queryConfig.getWhereCondition())) {
            sql.append(" WHERE ").append(queryConfig.getWhereCondition());
        }

        return sql.toString();
    }
}

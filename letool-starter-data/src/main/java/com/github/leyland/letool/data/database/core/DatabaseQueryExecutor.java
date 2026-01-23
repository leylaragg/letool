package com.github.leyland.letool.data.database.core;

import com.github.leyland.letool.data.database.builder.DatabaseQueryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * @ClassName <h2>DatabaseQueryExecutor</h2>
 * @Description     数据库查询执行器
 *                      执行数据库查询，支持分页和流式处理
 * @Author leyland
 * @Date 2026/01/16
 * @Version 1.0
 **/
@Slf4j
//@Component
public class DatabaseQueryExecutor {

    private final DatabaseManager databaseManager;

    private final DatabaseQueryBuilder queryBuilder;

    private DatabaseConfig config;

    // JdbcTemplate缓存，避免重复创建
    private final Map<String, JdbcTemplate> jdbcTemplateCache = new ConcurrentHashMap<>();
    private final Map<String, NamedParameterJdbcTemplate> namedParamJdbcTemplateCache = new ConcurrentHashMap<>();

    public DatabaseQueryExecutor(DatabaseManager databaseManager, DatabaseQueryBuilder queryBuilder) {
        this.databaseManager = databaseManager;
        this.queryBuilder = queryBuilder;
    }

    /**
     * 读取表的所有数据（一次性加载，适合数据量小的场景）
     *
     * @param queryKey 查询配置标识
     * @return 读取到的数据列表
     */
    public List<Map<String, Object>> queryData(String queryKey) {
        return queryData(queryKey, null, null);
    }

    /**
     * 读取表数据（带动态参数）
     */
    public List<Map<String, Object>> queryData(String queryKey, Map<String, Object> dynamicParams) {
        return queryData(queryKey, dynamicParams, null);
    }

    /**
     * 读取表数据（带自定义SQL）
     */
    public List<Map<String, Object>> queryDataWithCustomSql(String customSql, Map<String, Object> params, String datasourceName) {
        return queryDataWithCustomSql(customSql, params, datasourceName, null);
    }

    /**
     * 读取表数据（完全自定义）
     *
     * @param queryKey 查询配置标识（可选）
     * @param dynamicParams 动态参数
     * @param customSql 自定义SQL（可选，如果提供则忽略queryKey）
     * @return 读取到的数据列表
     */
    public List<Map<String, Object>> queryData(String queryKey, Map<String, Object> dynamicParams, String customSql) {
        DatabaseConfig.QueryConfig queryConfig = getQueryConfig(queryKey);

        // 如果提供了自定义SQL，使用自定义SQL
        if (customSql != null && !customSql.trim().isEmpty()) {
            String datasource = queryConfig != null ? queryConfig.getDatasource() : null;
            return executeSqlQuery(datasource, customSql, dynamicParams);
        }

        if (queryConfig == null) {
            throw new IllegalArgumentException("未找到查询配置: " + queryKey);
        }

        log.info("开始查询数据，查询标识: {}, 参数: {}", queryKey, dynamicParams);

        List<Map<String, Object>> allData = new ArrayList<>();
        Integer offset = 0;
        int batchCount = 0;

        while (true) {
            batchCount++;

            // 批量查询
            List<Map<String, Object>> batchData = executeBatchQuery(queryConfig, offset, dynamicParams);

            if (batchData.isEmpty()) {
                log.info("数据查询完成，共读取{}批次数据", batchCount - 1);
                break;
            }

            allData.addAll(batchData);
            offset = getOffset(batchData, offset + queryConfig.getBatchSize());

            log.debug("第{}批次读取完成，本次读取{}条，累计{}条", batchCount, batchData.size(), allData.size());
            sleepBriefly(50);
        }

        log.info("数据查询完成，查询标识: {}, 总数据量: {}", queryKey, allData.size());
        return allData;
    }

    /**
     * 使用自定义SQL查询数据（已废弃，保留兼容性）
     */
    private List<Map<String, Object>> queryDataWithCustomSql(String customSql, Map<String, Object> params, String datasourceName, DatabaseConfig.QueryConfig queryConfig) {
        log.info("执行自定义SQL查询，数据源: {}", datasourceName);
        List<Map<String, Object>> result = executeSqlQuery(datasourceName, customSql, params);
        log.info("自定义SQL查询完成，返回{}条数据", result.size());
        return result;
    }

    /**
     * 流式查询数据（适合大数据量场景，避免内存溢出）
     *
     * @param queryKey 查询配置标识
     * @param dataConsumer 数据消费者，处理每一条数据
     */
    public void streamQuery(String queryKey, Consumer<Map<String, Object>> dataConsumer) {
        streamQuery(queryKey, null, null, dataConsumer);
    }

    /**
     * 流式查询数据（带动态参数）
     */
    public void streamQuery(String queryKey, Map<String, Object> dynamicParams, Consumer<Map<String, Object>> dataConsumer) {
        streamQuery(queryKey, dynamicParams, null, dataConsumer);
    }

    /**
     * 流式查询数据（带自定义SQL）
     */
    public void streamQueryWithCustomSql(String customSql, Map<String, Object> params, String datasourceName, Consumer<Map<String, Object>> dataConsumer) {
        streamQuery(null, params, customSql, dataConsumer);
    }

    /**
     * 流式查询数据（完全自定义）
     *
     * @param queryKey 查询配置标识（可选）
     * @param dynamicParams 动态参数
     * @param customSql 自定义SQL（可选，如果提供则忽略queryKey）
     * @param dataConsumer 数据消费者
     */
    public void streamQuery(String queryKey, Map<String, Object> dynamicParams, String customSql, Consumer<Map<String, Object>> dataConsumer) {
        DatabaseConfig.QueryConfig queryConfig = null;

        // 如果提供了自定义SQL，使用自定义SQL
        if (customSql != null && !customSql.trim().isEmpty()) {
            String datasource = null;
            if (queryKey != null) {
                queryConfig = getQueryConfig(queryKey);
                datasource = queryConfig != null ? queryConfig.getDatasource() : null;
            }
            executeSqlStreamQuery(datasource, customSql, dynamicParams, dataConsumer);
            return;
        }

        queryConfig = getQueryConfig(queryKey);
        if (queryConfig == null) {
            throw new IllegalArgumentException("未找到查询配置: " + queryKey);
        }

        log.info("开始流式查询数据，查询标识: {}, 参数: {}", queryKey, dynamicParams);

        Integer offset = queryConfig.getBatchSize();
        int totalCount = 0;
        int batchCount = 0;

        while (true) {
            batchCount++;

            // 批量查询
            List<Map<String, Object>> batchData = executeBatchQuery(queryConfig, offset, dynamicParams);

            if (batchData.isEmpty()) {
                log.info("流式查询完成，查询 {} 表，共处理{}批次，{}条数据", queryKey, batchCount - 1, totalCount);
                break;
            }

            // 流式处理数据
            totalCount += processBatchData(batchData, dataConsumer);

            offset = getOffset(batchData, offset + queryConfig.getBatchSize());

            if (batchCount % 10 == 0) {
                log.info("流式查询进度: 已处理{}批次，{}条数据", batchCount, totalCount);
            }
            sleepBriefly(30);
        }
    }

    /**
     * 使用自定义SQL流式查询数据（已废弃，保留兼容性）
     */
    private void streamQueryWithCustomSql2(String customSql, Map<String, Object> params, String datasourceName, Consumer<Map<String, Object>> dataConsumer) {
        log.info("执行自定义SQL流式查询，数据源: {}", datasourceName);
        executeSqlStreamQuery(datasourceName, customSql, params, dataConsumer);
        log.info("自定义SQL流式查询完成");
    }

    /**
     * 获取表的数据总量
     */
    public long getDataCount(String queryKey) {
        DatabaseConfig.QueryConfig queryConfig = getQueryConfig(queryKey);
        JdbcTemplate jdbcTemplate = getJdbcTemplate(queryConfig.getDatasource());

        String countSql = queryBuilder.buildCountSQL(queryConfig);
        Long count = jdbcTemplate.queryForObject(countSql, Long.class);

        log.debug("查询标识: {} 的数据总量: {}", queryKey, count);
        return count != null ? count : 0L;
    }

    /**
     * 测试查询配置是否有效
     */
    public boolean testQueryConfig(String queryKey) {
        try {
            DatabaseConfig.QueryConfig queryConfig = getQueryConfig(queryKey);
            if (queryConfig == null) {
                return false;
            }

            // 测试数据源连接
            if (!databaseManager.testConnection(queryConfig.getDatasource())) {
                return false;
            }

            // 测试SQL语法（执行计数查询）
            getDataCount(queryKey);
            return true;

        } catch (Exception e) {
            log.error("测试查询配置失败: {}", queryKey, e);
            return false;
        }
    }

    /**
     * 获取查询配置信息
     */
    public DatabaseConfig.QueryConfig getQueryConfig(String queryKey) {
        if (config == null || config.getQueries() == null) {
            return null;
        }
        return config.getQueries().stream()
                .filter(q -> queryKey.equals(q.getQueryKey()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 设置配置
     */
    public void setConfig(DatabaseConfig config) {
        this.config = config;
    }

    // ============ 私有工具方法 ============

    /**
     * 获取JdbcTemplate
     */
    private JdbcTemplate getJdbcTemplate(String datasourceName) {
        String cacheKey = datasourceName != null ? datasourceName : "defaultDatasource";

        if (!jdbcTemplateCache.containsKey(cacheKey)) {
            synchronized (this) {
                if (!jdbcTemplateCache.containsKey(cacheKey)) {
                    jdbcTemplateCache.put(cacheKey, new JdbcTemplate(databaseManager.getDataSource(null)));
                }
            }
        }
        return jdbcTemplateCache.get(cacheKey);
    }

    /**
     * 获取NamedParameterJdbcTemplate
     */
    private NamedParameterJdbcTemplate getNamedParameterJdbcTemplate(String datasourceName) {
        String cacheKey = datasourceName != null ? datasourceName : "defaultDatasource";

        if (!namedParamJdbcTemplateCache.containsKey(cacheKey)) {
            synchronized (this) {
                if (!namedParamJdbcTemplateCache.containsKey(cacheKey)) {
                    namedParamJdbcTemplateCache.put(cacheKey, new NamedParameterJdbcTemplate(databaseManager.getDataSource(null)));
                }
            }
        }
        return namedParamJdbcTemplateCache.get(cacheKey);
    }

    /**
     * 执行SQL查询（通用方法）
     */
    private List<Map<String, Object>> executeSqlQuery(String datasourceName, String sql, Map<String, Object> params) {
        if (params != null && !params.isEmpty()) {
            NamedParameterJdbcTemplate template = getNamedParameterJdbcTemplate(datasourceName);
            return template.queryForList(sql, params);
        } else {
            JdbcTemplate template = getJdbcTemplate(datasourceName);
            return template.queryForList(sql);
        }
    }

    /**
     * 执行批量查询
     */
    private List<Map<String, Object>> executeBatchQuery(DatabaseConfig.QueryConfig queryConfig, Integer offset, Map<String, Object> dynamicParams) {
        DatabaseQueryBuilder.SqlWithParams sqlWithParams = queryBuilder.buildPaginationSQL(queryConfig, offset, dynamicParams);

        if (sqlWithParams.hasParams()) {
            NamedParameterJdbcTemplate template = getNamedParameterJdbcTemplate(queryConfig.getDatasource());
            return template.queryForList(sqlWithParams.getSql(), sqlWithParams.getParams());
        } else {
            JdbcTemplate template = getJdbcTemplate(queryConfig.getDatasource());
            return template.queryForList(sqlWithParams.getSql());
        }
    }

    /**
     * 执行SQL流式查询
     */
    private void executeSqlStreamQuery(String datasourceName, String customSql, Map<String, Object> params, Consumer<Map<String, Object>> dataConsumer) {
        List<Map<String, Object>> result = executeSqlQuery(datasourceName, customSql, params);
        processBatchData(result, dataConsumer);
    }

    /**
     * 处理批量数据
     */
    private int processBatchData(List<Map<String, Object>> batchData, Consumer<Map<String, Object>> dataConsumer) {
        int processedCount = 0;
        for (Map<String, Object> row : batchData) {
            try {
                dataConsumer.accept(row);
                processedCount++;
            } catch (Exception e) {
                log.error("处理数据行时发生异常，行数据: {}", row, e);
            }
        }
        return processedCount;
    }

    private Integer getOffset(List<Map<String, Object>> batchData, Integer offset) {
        if (batchData.isEmpty()) {
            return 0;
        }
        return offset;
    }

    /**
     * 短暂休眠，避免对源数据库造成压力
     */
    private void sleepBriefly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("线程休眠被中断");
        }
    }
}

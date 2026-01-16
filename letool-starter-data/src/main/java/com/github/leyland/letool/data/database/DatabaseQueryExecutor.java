package com.github.leyland.letool.data.database;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
@Component
public class DatabaseQueryExecutor {

    private final DatabaseManager databaseManager;

    private final DatabaseQueryBuilder queryBuilder;

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
            return queryDataWithCustomSql(customSql, dynamicParams, datasource, queryConfig);
        }

        if (queryConfig == null) {
            throw new IllegalArgumentException("未找到查询配置: " + queryKey);
        }

        log.info("开始查询数据，查询标识: {}, 参数: {}", queryKey, dynamicParams);

        // 使用NamedParameterJdbcTemplate支持命名参数
        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(
                databaseManager.getDataSource(queryConfig.getDatasource()));

        List<Map<String, Object>> allData = new ArrayList<>();
        Integer offset = 0;
        int batchCount = 0;
        boolean hasMoreData = true;

        while (hasMoreData) {
            batchCount++;

            // 构建SQL和参数
            DatabaseQueryBuilder.SqlWithParams sqlWithParams = queryBuilder.buildPaginationSQL(queryConfig, offset, dynamicParams);

            List<Map<String, Object>> batchData;
            if (sqlWithParams.hasParams()) {
                // 使用命名参数查询
                batchData = jdbcTemplate.queryForList(sqlWithParams.getSql(), sqlWithParams.getParams());
            } else {
                // 无参数查询
                JdbcTemplate simpleTemplate = new JdbcTemplate(
                        databaseManager.getDataSource(queryConfig.getDatasource()));
                batchData = simpleTemplate.queryForList(sqlWithParams.getSql());
            }

            if (batchData.isEmpty()) {
                hasMoreData = false;
                log.info("数据查询完成，共读取{}批次数据", batchCount - 1);
                continue;
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
     * 使用自定义SQL查询数据
     */
    private List<Map<String, Object>> queryDataWithCustomSql(String customSql, Map<String, Object> params, String datasourceName, DatabaseConfig.QueryConfig queryConfig) {
        log.info("执行自定义SQL查询，数据源: {}", datasourceName);

        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(databaseManager.getDataSource(datasourceName));

        List<Map<String, Object>> result;
        if (params != null && !params.isEmpty()) {
            result = jdbcTemplate.queryForList(customSql, params);
        } else {
            JdbcTemplate simpleTemplate = new JdbcTemplate(databaseManager.getDataSource(datasourceName));
            result = simpleTemplate.queryForList(customSql);
        }

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
            streamQueryWithCustomSql2(customSql, dynamicParams, datasource, dataConsumer);
            return;
        }

        queryConfig = getQueryConfig(queryKey);
        if (queryConfig == null) {
            throw new IllegalArgumentException("未找到查询配置: " + queryKey);
        }

        log.info("开始流式查询数据，查询标识: {}, 参数: {}", queryKey, dynamicParams);

        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(databaseManager.getDataSource(queryConfig.getDatasource()));

        Integer offset = queryConfig.getBatchSize();
        int totalCount = 0;
        int batchCount = 0;
        boolean hasMoreData = true;

        while (hasMoreData) {
            batchCount++;

            DatabaseQueryBuilder.SqlWithParams sqlWithParams = queryBuilder.buildPaginationSQL(queryConfig, offset, dynamicParams);

            List<Map<String, Object>> batchData;
            if (sqlWithParams.hasParams()) {
                batchData = jdbcTemplate.queryForList(sqlWithParams.getSql(), sqlWithParams.getParams());
            } else {
                JdbcTemplate simpleTemplate = new JdbcTemplate(databaseManager.getDataSource(queryConfig.getDatasource()));
                batchData = simpleTemplate.queryForList(sqlWithParams.getSql());
            }

            if (batchData.isEmpty()) {
                hasMoreData = false;
                log.info("流式查询完成，查询 {} 表，共处理{}批次，{}条数据", queryKey, batchCount - 1, totalCount);
                continue;
            }

            for (Map<String, Object> row : batchData) {
                try {
                    dataConsumer.accept(row);
                    totalCount++;
                } catch (Exception e) {
                    log.error("处理数据行时发生异常，行数据: {}", row, e);
                }
            }

            offset = getOffset(batchData, offset + queryConfig.getBatchSize());

            if (batchCount % 10 == 0) {
                log.info("流式查询进度: 已处理{}批次，{}条数据", batchCount, totalCount);
            }

            sleepBriefly(30);
        }
    }

    /**
     * 使用自定义SQL流式查询数据
     */
    private void streamQueryWithCustomSql2(String customSql, Map<String, Object> params, String datasourceName, Consumer<Map<String, Object>> dataConsumer) {
        log.info("执行自定义SQL流式查询，数据源: {}", datasourceName);

        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(databaseManager.getDataSource(datasourceName));

        List<Map<String, Object>> result;
        if (params != null && !params.isEmpty()) {
            result = jdbcTemplate.queryForList(customSql, params);
        } else {
            JdbcTemplate simpleTemplate = new JdbcTemplate(databaseManager.getDataSource(datasourceName));
            result = simpleTemplate.queryForList(customSql);
        }

        int totalCount = 0;
        for (Map<String, Object> row : result) {
            try {
                dataConsumer.accept(row);
                totalCount++;
            } catch (Exception e) {
                log.error("处理数据行时发生异常，行数据: {}", row, e);
            }
        }

        log.info("自定义SQL流式查询完成，共处理{}条数据", totalCount);
    }

    /**
     * 获取表的数据总量
     */
    public long getDataCount(String queryKey) {
        DatabaseConfig.QueryConfig queryConfig = getQueryConfig(queryKey);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(databaseManager.getDataSource(queryConfig.getDatasource()));

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
        // 这里需要从配置中获取，暂时返回null
        // 后续需要注入DatabaseConfig
        return null;
    }

    /**
     * 设置配置
     */
    public void setConfig(DatabaseConfig config) {
        // TODO: 暂时未实现
    }

    // ============ 私有工具方法 ============

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
}

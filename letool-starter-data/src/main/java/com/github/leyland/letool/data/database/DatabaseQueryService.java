package com.github.leyland.letool.data.database;

import com.github.leyland.letool.data.database.core.DatabaseConfig;
import com.github.leyland.letool.data.database.core.DatabaseQueryExecutor;
import com.github.leyland.letool.data.database.core.QueryContext;
import com.github.leyland.letool.data.database.handler.DataHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @ClassName <h2>DatabaseQueryService</h2>
 * @Description     数据库查询服务
 *                      提供对外统一的查询接口，支持自定义数据处理器
 * @Author leyland
 * @Date 2026/01/16
 * @Version 1.0
 **/
@Slf4j
@ConditionalOnProperty(prefix = "letool.database", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DatabaseQueryService {

    private final DatabaseQueryExecutor queryExecutor;

    private final DataHandlerFactory handlerFactory;

    /**
     * 查询数据（使用默认处理器）
     *
     * @param queryKey 查询键
     * @return 查询结果
     */
    public Object queryData(String queryKey) {
        return queryData(queryKey, null);
    }

    /**
     * 查询数据（使用默认处理器，带参数）
     *
     * @param queryKey 查询键
     * @param params 查询参数
     * @return 查询结果
     */
    public Object queryData(String queryKey, Map<String, Object> params) {
        return queryData(queryKey, params, null);
    }

    /**
     * 查询数据（使用自定义处理器）
     *
     * @param queryKey 查询键
     * @param params 查询参数
     * @param mainData 主数据（可选）
     * @return 查询结果
     */
    public Object queryData(String queryKey, Map<String, Object> params, Map<String, Object> mainData) {
        return queryData(queryKey, params, mainData, null);
    }

    /**
     * 查询数据（完全自定义）
     *
     * @param queryKey 查询键（可选，如果提供customSql则为可选）
     * @param params 查询参数
     * @param mainData 主数据（可选）
     * @param customSql 自定义SQL（可选）
     * @return 查询结果
     */
    public Object queryData(String queryKey, Map<String, Object> params, Map<String, Object> mainData, String customSql) {
        // 获取数据处理器
        DataHandler handler = handlerFactory.getHandler(queryKey);

        // 构建查询上下文
        QueryContext context = buildQueryContext(queryKey, params, mainData, customSql);

        // 调用处理器
        return handler.handleData(queryKey, context);
    }

    /**
     * 查询数据（直接返回List<Map>）
     *
     * @param queryKey 查询键
     * @return 查询结果
     */
    public List<Map<String, Object>> queryDataAsList(String queryKey) {
        return queryDataAsList(queryKey, null);
    }

    /**
     * 查询数据（直接返回List<Map>，带参数）
     *
     * @param queryKey 查询键
     * @param params 查询参数
     * @return 查询结果
     */
    public List<Map<String, Object>> queryDataAsList(String queryKey, Map<String, Object> params) {
        return queryDataAsList(queryKey, params, null);
    }

    /**
     * 查询数据（直接返回List<Map>，完全自定义）
     *
     * @param queryKey 查询键（可选，如果提供customSql则为可选）
     * @param params 查询参数
     * @param customSql 自定义SQL（可选）
     * @return 查询结果
     */
    public List<Map<String, Object>> queryDataAsList(String queryKey, Map<String, Object> params, String customSql) {
        // 如果提供了自定义SQL，直接查询
        if (customSql != null && !customSql.trim().isEmpty()) {
            String datasourceName = queryKey != null ? getDatasourceName(queryKey) : null;
            return queryExecutor.queryDataWithCustomSql(customSql, params, datasourceName);
        }

        // 否则使用配置的查询键
        return queryExecutor.queryData(queryKey, params);
    }

    /**
     * 流式查询数据（使用默认处理器）
     *
     * @param queryKey 查询键
     * @param dataConsumer 数据消费者
     */
    public void streamData(String queryKey, Consumer<Map<String, Object>> dataConsumer) {
        streamData(queryKey, null, dataConsumer);
    }

    /**
     * 流式查询数据（使用默认处理器，带参数）
     *
     * @param queryKey 查询键
     * @param params 查询参数
     * @param dataConsumer 数据消费者
     */
    public void streamData(String queryKey, Map<String, Object> params, Consumer<Map<String, Object>> dataConsumer) {
        queryExecutor.streamQuery(queryKey, params, dataConsumer);
    }

    /**
     * 流式查询数据（自定义SQL）
     *
     * @param customSql 自定义SQL
     * @param params 查询参数
     * @param datasourceName 数据源名称（可选）
     * @param dataConsumer 数据消费者
     */
    public void streamDataWithCustomSql(String customSql, Map<String, Object> params, String datasourceName, Consumer<Map<String, Object>> dataConsumer) {
        queryExecutor.streamQueryWithCustomSql(customSql, params, datasourceName, dataConsumer);
    }

    /**
     * 获取数据总量
     *
     * @param queryKey 查询键
     * @return 数据总量
     */
    public long getDataCount(String queryKey) {
        return queryExecutor.getDataCount(queryKey);
    }

    /**
     * 测试查询配置是否有效
     *
     * @param queryKey 查询键
     * @return 是否有效
     */
    public boolean testQueryConfig(String queryKey) {
        return queryExecutor.testQueryConfig(queryKey);
    }

    // ============ 私有方法 ============

    /**
     * 构建查询上下文
     */
    private QueryContext buildQueryContext(String queryKey, Map<String, Object> params, Map<String, Object> mainData, String customSql) {
        QueryContext context = new QueryContext();
        context.setDynamicParams(params);
        context.setMainData(mainData);
        context.setCustomSql(customSql);
        context.setDatasourceName(customSql != null ? getDatasourceName(queryKey) : null);

        // 构建查询执行器
        context.setQueryExecutor(buildQueryExecutor());

        return context;
    }

    /**
     * 构建查询执行器
     */
    private QueryContext.QueryExecutor buildQueryExecutor() {
        return new QueryContext.QueryExecutor() {
            @Override
            public List<Map<String, Object>> executeQuery(String queryKey, Map<String, Object> params) {
                return queryExecutor.queryData(queryKey, params);
            }

            @Override
            public List<Map<String, Object>> executeCustomQuery(String sql, Map<String, Object> params, String datasourceName) {
                return queryExecutor.queryDataWithCustomSql(sql, params, datasourceName);
            }

            @Override
            public void streamQuery(String queryKey, Map<String, Object> params, Consumer<Map<String, Object>> dataConsumer) {
                queryExecutor.streamQuery(queryKey, params, dataConsumer);
            }

            @Override
            public void streamCustomQuery(String sql, Map<String, Object> params, String datasourceName, Consumer<Map<String, Object>> dataConsumer) {
                queryExecutor.streamQueryWithCustomSql(sql, params, datasourceName, dataConsumer);
            }
        };
    }

    /**
     * 获取数据源名称
     */
    private String getDatasourceName(String queryKey) {
        DatabaseConfig.QueryConfig queryConfig = queryExecutor.getQueryConfig(queryKey);
        return queryConfig != null ? queryConfig.getDatasource() : null;
    }
}

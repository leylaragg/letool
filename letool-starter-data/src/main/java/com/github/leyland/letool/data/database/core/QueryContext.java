package com.github.leyland.letool.data.database.core;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * @ClassName <h2>QueryContext</h2>
 * @Description     查询上下文
 *                      包含查询所需的所有信息和回调接口
 * @Author leyland
 * @Date 2026/01/16
 * @Version 1.0
 **/
@Data
public class QueryContext {

    /**
     * 动态参数
     */
    private Map<String, Object> dynamicParams;

    /**
     * 主数据（如患者信息）
     */
    private Map<String, Object> mainData;

    /**
     * 自定义SQL（可选）
     */
    private String customSql;

    /**
     * 数据源名称（可选）
     */
    private String datasourceName;

    /**
     * 查询执行回调接口
     */
    private QueryExecutor queryExecutor;

    /**
     * 查询执行器接口
     */
    public interface QueryExecutor {
        /**
         * 执行查询
         *
         * @param queryKey 查询键
         * @param params 查询参数
         * @return 查询结果
         */
        List<Map<String, Object>> executeQuery(String queryKey, Map<String, Object> params);

        /**
         * 执行自定义SQL查询
         *
         * @param sql SQL语句
         * @param params 查询参数
         * @param datasourceName 数据源名称
         * @return 查询结果
         */
        List<Map<String, Object>> executeCustomQuery(String sql, Map<String, Object> params, String datasourceName);

        /**
         * 流式查询
         *
         * @param queryKey 查询键
         * @param params 查询参数
         * @param dataConsumer 数据消费者
         */
        void streamQuery(String queryKey, Map<String, Object> params, java.util.function.Consumer<Map<String, Object>> dataConsumer);

        /**
         * 流式查询自定义SQL
         *
         * @param sql SQL语句
         * @param params 查询参数
         * @param datasourceName 数据源名称
         * @param dataConsumer 数据消费者
         */
        void streamCustomQuery(String sql, Map<String, Object> params, String datasourceName, java.util.function.Consumer<Map<String, Object>> dataConsumer);
    }
}

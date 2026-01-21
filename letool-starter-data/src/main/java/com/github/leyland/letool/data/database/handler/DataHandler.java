package com.github.leyland.letool.data.database.handler;

import com.github.leyland.letool.data.database.core.QueryContext;

/**
 * @ClassName <h2>DataHandler</h2>
 * @Description     数据处理器接口
 *                      用于处理查询数据的自定义逻辑
 * @Author leyland
 * @Date 2026/01/16
 * @Version 1.0
 **/
public interface DataHandler {

    /**
     * 处理查询数据
     *
     * @param queryKey 查询键
     * @param context 查询上下文
     * @return 处理后的数据
     */
    Object handleData(String queryKey, QueryContext context);

    /**
     * 是否支持此查询键
     *
     * @param queryKey 查询键
     * @return 是否支持
     */
    default boolean supports(String queryKey) {
        return true;
    }

    /**
     * 获取查询类型
     *
     * @param queryKey 查询键
     * @return 查询类型
     */
    default QueryType getQueryType(String queryKey) {
        return QueryType.ONE_TO_ONE;
    }

    /**
     * 查询类型枚举
     */
    enum QueryType {
        ONE_TO_ONE,      // 一对一
        ONE_TO_MANY,     // 一对多
        CUSTOM          // 自定义
    }
}

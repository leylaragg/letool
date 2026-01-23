package com.github.leyland.letool.data.database.handler;

import com.github.leyland.letool.data.database.core.QueryContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * @ClassName <h2>DefaultDataHandler</h2>
 * @Description     默认数据处理器
 *                      处理标准的查询逻辑，支持一对一和一对多查询
 * @Author leyland
 * @Date 2026/01/16
 * @Version 1.0
 **/
@Slf4j
@Component("defaultDataHandler")
@RequiredArgsConstructor
public class DefaultDataHandler extends AbstractDataHandler {

    @Override
    public Object handleData(String queryKey, QueryContext context) {
        // 如果没有查询执行器，返回空
        if (context.getQueryExecutor() == null) {
            log.warn("查询执行器未配置，查询键: {}", queryKey);
            return null;
        }

        // 使用查询执行器获取数据
        QueryContext.QueryExecutor executor = context.getQueryExecutor();

        // 判断查询类型
        QueryType queryType = getQueryType(queryKey);

        if (queryType == QueryType.ONE_TO_MANY) {
            // 一对多查询：使用流式查询
            List<Map<String, Object>> result = new ArrayList<>();
            executor.streamQuery(queryKey, context.getDynamicParams(), data -> result.add(data));
            log.debug("一对多查询 {} 返回 {} 条数据", queryKey, result.size());
            return result;
        } else {
            // 一对一查询：返回单条Map
            List<Map<String, Object>> result = executor.executeQuery(queryKey, context.getDynamicParams());
            if (result == null || result.isEmpty()) {
                return null;
            }
            return result.get(0);
        }
    }

    @Override
    public QueryType getQueryType(String queryKey) {
        if (queryKey == null) {
            throw new RuntimeException("queryKey 不能为空，请联系数据管理员");
        }

        // 默认一对一，子类可以覆盖此方法
        return QueryType.ONE_TO_ONE;
    }
}

package com.github.leyland.letool.data.database.example;

import com.github.leyland.letool.data.database.DatabaseQueryService;
import com.github.leyland.letool.data.database.handler.AbstractDataHandler;
import com.github.leyland.letool.data.database.handler.DataHandler;
import com.github.leyland.letool.data.database.handler.QueryContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;

/**
 * @ClassName <h2>DatabaseUsageExample</h2>
 * @Description     Database 包使用示例
 *                      展示如何使用 Database 包的各种功能
 * @Author leyland
 * @Date 2026/01/16
 * @Version 1.0
 **/
@Slf4j
@Component
@ConditionalOnProperty(prefix = "letool.database", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DatabaseUsageExample {

    private final DatabaseQueryService databaseQueryService;

    /**
     * 示例 1：基础查询（使用配置的查询键）
     */
    public void example1_BasicQuery() {
        // 简单查询，返回处理后的数据（根据处理器返回类型可能是单条或列表）
        Object result = databaseQueryService.queryData("patient_info");
        log.info("查询结果: {}", result);

        // 带参数的查询
        Map<String, Object> params = new HashMap<>();
        params.put("patientId", "P001");
        result = databaseQueryService.queryData("patient_info", params);
        log.info("带参数查询结果: {}", result);
    }

    /**
     * 示例 2：直接查询（返回 List<Map>）
     */
    public void example2_QueryAsList() {
        // 查询并返回 List<Map<String, Object>> 形式
        List<Map<String, Object>> result = databaseQueryService.queryDataAsList("patient_info");
        log.info("查询到 {} 条记录", result.size());

        // 带参数的查询
        Map<String, Object> params = new HashMap<>();
        params.put("startTime", "2024-01-01");
        List<Map<String, Object>> resultWithParams = databaseQueryService.queryDataAsList("lab_result", params);
        log.info("带参数查询到 {} 条记录", resultWithParams.size());
    }

    /**
     * 示例 3：使用自定义SQL查询
     */
    public void example3_CustomSqlQuery() {
        // 自定义 SQL 查询
        String customSql = "SELECT id, name, age FROM patient_info WHERE age > :minAge LIMIT 100";
        Map<String, Object> params = new HashMap<>();
        params.put("minAge", 18);

        List<Map<String, Object>> result = databaseQueryService.queryDataAsList(null, params, customSql);
        log.info("自定义SQL查询到 {} 条记录", result.size());
    }

    /**
     * 示例 4：流式查询（适合大数据量）
     */
    public void example4_StreamQuery() {
        // 流式查询，逐条处理数据，避免内存溢出
        Map<String, Object> params = new HashMap<>();
        params.put("patientId", "P001");

        databaseQueryService.streamData("outpat_record", params, data -> {
            // 处理每一条数据
            log.info("处理记录: {}", data.get("id"));
            // 可以在这里进行业务处理，如：保存到其他表、发送消息等
        });

        // 使用自定义SQL进行流式查询
        String customSql = "SELECT * FROM lab_result WHERE test_time >= :startTime";
        Map<String, Object> sqlParams = new HashMap<>();
        sqlParams.put("startTime", "2024-01-01");

        databaseQueryService.streamDataWithCustomSql(customSql, sqlParams, null, data -> {
            log.info("处理自定义SQL记录: {}", data.get("id"));
        });
    }

    /**
     * 示例 5：使用自定义数据处理器
     */
    public void example5_CustomHandler() {
        // 带主数据的查询（适合场景：先查主表，再查关联表）
        Map<String, Object> mainData = new HashMap<>();
        mainData.put("patient_id", "P001");
        mainData.put("name", "张三");

        Map<String, Object> params = new HashMap<>();
        params.put("patientId", "P001");

        // 使用自定义处理器（需要在 DataHandlerFactory 中注册）
        Object result = databaseQueryService.queryData("lab_result", params, mainData);
        log.info("自定义处理器查询结果: {}", result);
    }

    /**
     * 示例 6：获取数据总量和测试配置
     */
    public void example6_MetaData() {
        // 获取数据总量
        long count = databaseQueryService.getDataCount("patient_info");
        log.info("patient_info 表共有 {} 条记录", count);

        // 测试查询配置是否有效
        boolean isValid = databaseQueryService.testQueryConfig("patient_info");
        log.info("查询配置是否有效: {}", isValid);
    }

    /**
     * 示例 7：自定义数据处理器实现
     */
    @Component("customLabResultHandler")
    @ConditionalOnProperty(prefix = "letool.database", name = "enabled", havingValue = "true")
    @RequiredArgsConstructor
    public static class CustomLabResultHandler extends AbstractDataHandler implements DataHandler {

        // 可以注入其他服务
        // private final SomeService someService;

        @Override
        public Object handleData(String queryKey, QueryContext context) {
            if (!"lab_result".equals(queryKey)) {
                return null;
            }

            // 获取主数据（如患者信息）
            Map<String, Object> mainData = context.getMainData();

            // 获取查询执行器
            QueryContext.QueryExecutor executor = context.getQueryExecutor();

            // 使用查询执行器获取数据
            List<Map<String, Object>> labResults = new ArrayList<>();
            Map<String, Object> params = context.getDynamicParams();

            if (params == null) {
                params = new HashMap<>();
            }

            // 添加患者ID参数
            if (mainData != null && mainData.containsKey("patient_id")) {
                params.put("patientId", mainData.get("patient_id"));
            }

            executor.streamQuery(queryKey, params, data -> {
                // 对每条数据进行自定义处理
                Map<String, Object> processedData = processLabResult(data);
                labResults.add(processedData);
            });

            log.info("自定义处理器处理了 {} 条检验结果", labResults.size());
            return labResults;
        }

        @Override
        public boolean supports(String queryKey) {
            return "lab_result".equals(queryKey);
        }

        @Override
        public QueryType getQueryType(String queryKey) {
            return QueryType.ONE_TO_MANY; // 检验结果是一对多关系
        }

        /**
         * 自定义处理逻辑
         */
        private Map<String, Object> processLabResult(Map<String, Object> rawResult) {
            Map<String, Object> result = new HashMap<>(rawResult);

            // 示例：添加处理时间
            result.put("process_time", new Date());

            // 示例：数值转换
            Object resultValue = rawResult.get("result_value");
            if (resultValue != null) {
                try {
                    double value = Double.parseDouble(resultValue.toString());
                    result.put("result_value_double", value);
                } catch (NumberFormatException e) {
                    log.warn("无法转换检验结果值: {}", resultValue);
                }
            }

            return result;
        }
    }
}

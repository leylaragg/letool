package database;

import com.github.leyland.letool.data.DatabaseApplication;
import com.github.leyland.letool.data.database.DatabaseQueryExecutor;
import com.github.leyland.letool.data.database.handler.DataHandler;
import com.github.leyland.letool.tool.configuration.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @ClassName <h2>DatabaseQueryExecutorTest</h2>
 * @Description     DatabaseQueryExecutor 测试类
 *                      测试底层的查询执行器
 * @Author leyland
 * @Date 2026/01/16
 * @Version 1.0
 **/
@Slf4j
@SpringBootTest(classes = DatabaseApplication.class)
@ActiveProfiles("test")
public class DatabaseQueryExecutorTest {

    @Autowired
    private DatabaseQueryExecutor queryExecutor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    public void setup() {
        log.info("========================================");
        log.info("测试准备：初始化查询执行器测试");
        log.info("========================================");

        // 检查表是否存在
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = SCHEMA()",
                String.class
        );
        log.info("数据库中的表: {}", tables);
    }

    @Test
    @DisplayName("测试1：基础查询 - 执行器层面")
    public void testExecutorBasicQuery() {
        log.info("\n--- 测试1：基础查询 - 执行器层面 ---\n");

        Map<String, DataHandler> beansOfType = SpringUtil.getBeansOfType(DataHandler.class);


        // 设置配置
        com.github.leyland.letool.data.database.DatabaseConfig.QueryConfig config = 
                createPatientInfoConfig();

        List<Map<String, Object>> result = queryExecutor.queryData(config.getQueryKey());

        assertNotNull(result);
        assertFalse(result.isEmpty());
        log.info("查询到 {} 条患者记录", result.size());
    }

    @Test
    @DisplayName("测试2：带参数查询 - 执行器层面")
    public void testExecutorQueryWithParams() {
        log.info("\n--- 测试2：带参数查询 - 执行器层面 ---\n");

        com.github.leyland.letool.data.database.DatabaseConfig.QueryConfig config = 
                createPatientInfoConfig();

        Map<String, Object> params = new HashMap<>();
        params.put("patientId", "P001");

        List<Map<String, Object>> result = queryExecutor.queryData(config.getQueryKey(), params);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());

        log.info("查询结果: {}", result.get(0));
    }

    @Test
    @DisplayName("测试3：自定义SQL查询 - 执行器层面")
    public void testExecutorCustomSqlQuery() {
        log.info("\n--- 测试3：自定义SQL查询 - 执行器层面 ---\n");

        String customSql = "SELECT patient_id, name, age, gender FROM patient_info WHERE age > :minAge ORDER BY age DESC LIMIT 5";

        Map<String, Object> params = new HashMap<>();
        params.put("minAge", 40);

        List<Map<String, Object>> result = queryExecutor.queryDataWithCustomSql(customSql, params, null);

        assertNotNull(result);
        assertFalse(result.isEmpty());

        result.forEach(patient -> {
            log.info("患者: {} - {}岁", patient.get("name"), patient.get("age"));
            assertTrue((Integer) patient.get("age") > 40);
        });
    }

    @Test
    @DisplayName("测试4：流式查询 - 执行器层面")
    public void testExecutorStreamQuery() {
        log.info("\n--- 测试4：流式查询 - 执行器层面 ---\n");

        Map<String, Object> params = new HashMap<>();
        params.put("patientId", "P002");

        AtomicInteger count = new AtomicInteger(0);

        queryExecutor.streamQuery("lab_report", params, data -> {
            count.incrementAndGet();
            log.info("处理检验报告 {}", data.get("id"));
        });

        log.info("流式查询共处理 {} 条记录", count.get());
        assertTrue(count.get() > 0);
    }

    @Test
    @DisplayName("测试5：获取数据总量 - 执行器层面")
    public void testExecutorGetDataCount() {
        log.info("\n--- 测试5：获取数据总量 - 执行器层面 ---\n");

        com.github.leyland.letool.data.database.DatabaseConfig.QueryConfig config = 
                createPatientInfoConfig();

        long count = queryExecutor.getDataCount(config.getQueryKey());
        log.info("患者信息总数: {}", count);
        assertTrue(count > 0);
    }

    // ==================== 私有辅助方法 ====================

    private com.github.leyland.letool.data.database.DatabaseConfig.QueryConfig createPatientInfoConfig() {
        com.github.leyland.letool.data.database.DatabaseConfig.QueryConfig config = 
                new com.github.leyland.letool.data.database.DatabaseConfig.QueryConfig();

        config.setQueryKey("patient_info");
        config.setTableName("patient_info");
        config.setDatasource(""); // 使用 Spring Boot 默认数据源
        config.setPrimaryKey("id");
        config.setBatchSize(100);
        config.setFields(java.util.Arrays.asList(
                "id", "patient_id", "name", "gender", "age", 
                "phone", "email", "address", "status", "create_time"
        ));
        config.setWhereCondition("status = 1");
        config.setOrderBy("create_time DESC");
        config.setStreamQuery(false);

        return config;
    }
}

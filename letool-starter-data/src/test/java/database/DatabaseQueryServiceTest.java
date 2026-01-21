package database;

import com.github.leyland.letool.data.DatabaseApplication;
import com.github.leyland.letool.data.database.DatabaseQueryService;
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
 * @ClassName <h2>DatabaseQueryServiceTest</h2>
 * @Description     DatabaseQueryService 测试类
 * @Author leyland
 * @Date 2026/01/16
 * @Version 1.0
 **/
@Slf4j
@SpringBootTest(classes = DatabaseApplication.class)
@ActiveProfiles("test")
public class DatabaseQueryServiceTest {

    @Autowired
    private DatabaseQueryService databaseQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    public void setup() {
        log.info("========================================");
        log.info("测试准备：初始化测试数据");
        log.info("========================================");
        // 初始化测试数据在 SQL 脚本中完成
    }

    @Test
    @DisplayName("测试1：基础查询 - 查询所有患者信息")
    public void testBasicQuery() {
        log.info("\n--- 测试1：基础查询 - 查询所有患者信息 ---\n");

        // 查询所有患者信息
        List<Map<String, Object>> result = databaseQueryService.queryDataAsList("patient_info");

        assertNotNull(result);
        assertFalse(result.isEmpty());
        log.info("查询到 {} 条患者记录", result.size());

        // 打印前3条记录
        result.stream().limit(3).forEach(patient -> {
            log.info("患者: ID={}, 患者编号={}, 姓名={}, 年龄={}, 性别={}",
                    patient.get("id"),
                    patient.get("patient_id"),
                    patient.get("name"),
                    patient.get("age"),
                    patient.get("gender"));
        });
    }

    @Test
    @DisplayName("测试2：带参数查询 - 根据患者ID查询")
    public void testQueryWithParams() {
        log.info("\n--- 测试2：带参数查询 - 根据患者ID查询 ---\n");

        // 构建查询参数
        Map<String, Object> params = new HashMap<>();
        params.put("patientId", "P001");

        // 查询特定患者的门诊记录
        List<Map<String, Object>> result = databaseQueryService.queryDataAsList("patient_info", params);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());

        Map<String, Object> patient = result.get(0);
        log.info("查询结果: ID={}, 患者编号={}, 姓名={}, 年龄={}",
                patient.get("id"),
                patient.get("patient_id"),
                patient.get("name"),
                patient.get("age"));
    }

    @Test
    @DisplayName("测试3：自定义SQL查询")
    public void testCustomSqlQuery() {
        log.info("\n--- 测试3：自定义SQL查询 ---\n");

        // 自定义 SQL：查询年龄大于30岁的患者
        String customSql = "SELECT * FROM patient_info WHERE age > :minAge AND status = 1 ORDER BY age DESC LIMIT 10";

        Map<String, Object> params = new HashMap<>();
        params.put("minAge", 30);

        List<Map<String, Object>> result = databaseQueryService.queryDataAsList(null, params, customSql);

        assertNotNull(result);
        log.info("自定义SQL查询到 {} 条记录", result.size());

        result.forEach(patient -> {
            log.info("患者: 姓名={}, 年龄={}", patient.get("name"), patient.get("age"));
            assertTrue((Integer) patient.get("age") > 30);
        });
    }

    @Test
    @DisplayName("测试4：一对多查询 - 查询患者门诊记录")
    public void testOneToManyQuery() {
        log.info("\n--- 测试4：一对多查询 - 查询患者门诊记录 ---\n");

        Map<String, Object> params = new HashMap<>();
        params.put("patientId", "P001");

        // 查询患者的门诊记录（一对多）
        List<Map<String, Object>> result = databaseQueryService.queryDataAsList("outpat_record", params);

        assertNotNull(result);
        log.info("患者 P001 的门诊记录数: {}", result.size());

        result.forEach(record -> {
            log.info("门诊记录: 就诊号={}, 就诊日期={}, 科室={}, 医生={}",
                    record.get("visit_no"),
                    record.get("visit_date"),
                    record.get("dept_name"),
                    record.get("doctor_name"));
        });
    }

    @Test
    @DisplayName("测试5：流式查询 - 处理大量数据")
    public void testStreamQuery() {
        log.info("\n--- 测试5：流式查询 - 处理大量数据 ---\n");

        Map<String, Object> params = new HashMap<>();
        params.put("patientId", "P002");

        AtomicInteger count = new AtomicInteger(0);

        // 流式查询，逐条处理数据
        databaseQueryService.streamData("lab_report", params, data -> {
            count.incrementAndGet();
            log.info("处理检验报告 {}: {} - {} ({})",
                    data.get("id"),
                    data.get("test_item"),
                    data.get("result_value"),
                    data.get("unit"));

            // 模拟数据处理
            if (count.get() <= 3) {
                // 可以在这里进行业务处理
            }
        });

        log.info("流式查询共处理 {} 条记录", count.get());
        assertTrue(count.get() > 0);
    }

    @Test
    @DisplayName("测试6：流式查询 - 自定义SQL")
    public void testStreamQueryWithCustomSql() {
        log.info("\n--- 测试6：流式查询 - 自定义SQL ---\n");

        // 自定义 SQL 流式查询
        String customSql = "SELECT * FROM exam_report WHERE patient_id = :patientId AND status = 1 ORDER BY exam_time DESC";

        Map<String, Object> params = new HashMap<>();
        params.put("patientId", "P003");

        AtomicInteger count = new AtomicInteger(0);

        databaseQueryService.streamDataWithCustomSql(customSql, params, null, data -> {
            count.incrementAndGet();
            log.info("处理检查报告 {}: {} - {}",
                    data.get("id"),
                    data.get("exam_item"),
                    data.get("exam_result"));
        });

        log.info("自定义SQL流式查询共处理 {} 条记录", count.get());
        assertTrue(count.get() > 0);
    }

    @Test
    @DisplayName("测试7：获取数据总量")
    public void testGetDataCount() {
        log.info("\n--- 测试7：获取数据总量 ---\n");

        // 获取患者信息总数
        long patientCount = databaseQueryService.getDataCount("patient_info");
        log.info("患者信息总数: {}", patientCount);
        assertTrue(patientCount > 0);

        // 获取门诊记录总数
        long visitCount = databaseQueryService.getDataCount("outpat_record");
        log.info("门诊记录总数: {}", visitCount);
        assertTrue(visitCount > 0);
    }

    @Test
    @DisplayName("测试8：测试查询配置")
    public void testQueryConfig() {
        log.info("\n--- 测试8：测试查询配置 ---\n");

        // 测试患者信息配置
        boolean isValid = databaseQueryService.testQueryConfig("patient_info");
        assertTrue(isValid);
        log.info("患者信息查询配置有效: {}", isValid);

        // 测试门诊记录配置
        isValid = databaseQueryService.testQueryConfig("outpat_record");
        assertTrue(isValid);
        log.info("门诊记录查询配置有效: {}", isValid);
    }

    @Test
    @DisplayName("测试9：复杂查询 - 患者统计摘要")
    public void testComplexQuery() {
        log.info("\n--- 测试9：复杂查询 - 患者统计摘要 ---\n");

        // 查询患者统计摘要（自定义SQL）
        Map<String, Object> params = new HashMap<>();
        params.put("minAge", 25);
        params.put("maxAge", 50);

        List<Map<String, Object>> result = databaseQueryService.queryDataAsList("patient_summary", params);

        assertNotNull(result);
        assertFalse(result.isEmpty());

        log.info("患者统计摘要查询到 {} 条记录", result.size());

        result.forEach(summary -> {
            log.info("患者: {} ({}) - 年龄: {} - 门诊: {}次, 检验: {}次, 检查: {}次",
                    summary.get("name"),
                    summary.get("patient_id"),
                    summary.get("age"),
                    summary.get("visit_count"),
                    summary.get("lab_count"),
                    summary.get("exam_count"));
        });
    }

    @Test
    @DisplayName("测试10：多表联合查询")
    public void testMultiTableQuery() {
        log.info("\n--- 测试10：多表联合查询 ---\n");

        // 查询患者的完整信息（基本信息 + 门诊记录 + 检验报告）
        String patientId = "P001";

        // 1. 查询患者基本信息
        Map<String, Object> patientParams = new HashMap<>();
        patientParams.put("patientId", patientId);
        List<Map<String, Object>> patientList = databaseQueryService.queryDataAsList("patient_info", patientParams);

        if (!patientList.isEmpty()) {
            Map<String, Object> patient = patientList.get(0);
            log.info("患者基本信息: {} - {}岁 - {}",
                    patient.get("name"),
                    patient.get("age"),
                    patient.get("gender"));

            // 2. 查询门诊记录
            List<Map<String, Object>> visits = databaseQueryService.queryDataAsList("outpat_record", patientParams);
            log.info("门诊记录: {}条", visits.size());

            // 3. 查询检验报告
            List<Map<String, Object>> labReports = databaseQueryService.queryDataAsList("lab_report", patientParams);
            log.info("检验报告: {}条", labReports.size());

            // 4. 查询检查报告
            List<Map<String, Object>> examReports = databaseQueryService.queryDataAsList("exam_report", patientParams);
            log.info("检查报告: {}条", examReports.size());

            assertTrue(!patientList.isEmpty());
        }
    }
}

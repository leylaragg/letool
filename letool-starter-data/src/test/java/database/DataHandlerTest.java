package database;

import com.github.leyland.letool.data.DatabaseApplication;
import com.github.leyland.letool.data.database.handler.AbstractDataHandler;
import com.github.leyland.letool.data.database.handler.DataHandler;
import com.github.leyland.letool.data.database.core.QueryContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @ClassName <h2>DataHandlerTest</h2>
 * @Description     DataHandler 自定义处理器测试
 * @Author leyland
 * @Date 2026/01/16
 * @Version 1.0
 **/
@Slf4j
@SpringBootTest(classes = {DatabaseApplication.class, DataHandlerTest.TestConfig.class})
public class DataHandlerTest {

    @Autowired
    private com.github.leyland.letool.data.database.DatabaseQueryService databaseQueryService;

    @BeforeEach
    public void setup() {
        log.info("========================================");
        log.info("测试准备：初始化自定义数据处理器测试");
        log.info("========================================");
    }

    @Test
    @DisplayName("测试1：使用自定义处理器查询检验报告")
    public void testCustomLabReportHandler() {
        log.info("\n--- 测试1：使用自定义处理器查询检验报告 ---\n");

        Map<String, Object> mainData = new HashMap<>();
        mainData.put("patient_id", "P002");
        mainData.put("name", "李四");

        Map<String, Object> params = new HashMap<>();
        params.put("patientId", "P002");

        // 使用自定义处理器
        Object result = databaseQueryService.queryData("lab_report", params, mainData);
        
        assertNotNull(result);
        assertTrue(result instanceof List);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> labReports = (List<Map<String, Object>>) result;
        
        log.info("自定义处理器查询到 {} 条检验报告", labReports.size());
        
        // 验证是否添加了自定义字段
        labReports.forEach(report -> {
            assertNotNull(report.get("processed_time"));
            log.info("检验报告: {} - {} (处理时间: {})",
                    report.get("test_item"),
                    report.get("result_value"),
                    report.get("processed_time"));
        });
    }

    @Test
    @DisplayName("测试2：使用自定义处理器查询检查报告")
    public void testCustomExamReportHandler() {
        log.info("\n--- 测试2：使用自定义处理器查询检查报告 ---\n");

        Map<String, Object> mainData = new HashMap<>();
        mainData.put("patient_id", "P003");
        mainData.put("name", "王五");

        Map<String, Object> params = new HashMap<>();
        params.put("patientId", "P003");

        Object result = databaseQueryService.queryData("exam_report", params, mainData);
        
        assertNotNull(result);
        assertTrue(result instanceof List);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> examReports = (List<Map<String, Object>>) result;
        
        log.info("自定义处理器查询到 {} 条检查报告", examReports.size());
        
        examReports.forEach(report -> {
            assertNotNull(report.get("exam_item_en")); // 检查是否添加了英文项目名
            log.info("检查报告: {} - {} ({}): {}",
                    report.get("exam_item"),
                    report.get("exam_item_en"),
                    report.get("impression"),
                    report.get("exam_result"));
        });
    }

    @Configuration
    static class TestConfig {
        
        /**
         * 自定义检验报告处理器
         */
        @Bean("labReportHandler")
        public DataHandler labReportHandler() {
            return new AbstractDataHandler() {
                @Override
                public Object handleData(String queryKey, QueryContext context) {
                    if (!"lab_report".equals(queryKey)) {
                        return null;
                    }

                    log.info("使用自定义检验报告处理器处理查询");

                    Map<String, Object> mainData = context.getMainData();
                    QueryContext.QueryExecutor executor = context.getQueryExecutor();

                    List<Map<String, Object>> labReports = new ArrayList<>();
                    Map<String, Object> params = context.getDynamicParams();

                    if (params == null) {
                        params = new HashMap<>();
                    }

                    if (mainData != null && mainData.containsKey("patient_id")) {
                        params.put("patientId", mainData.get("patient_id"));
                    }

                    executor.streamQuery(queryKey, params, data -> {
                        // 自定义处理：添加处理时间
                        Map<String, Object> processedData = new HashMap<>(data);
                        processedData.put("processed_time", new java.util.Date());
                        processedData.put("processed_by", "LabReportHandler");

                        // 示例：数值转换
                        Object resultValue = data.get("result_value");
                        if (resultValue != null && resultValue.toString().matches("\\d+\\.?\\d*")) {
                            try {
                                double value = Double.parseDouble(resultValue.toString());
                                processedData.put("result_value_numeric", value);
                            } catch (NumberFormatException e) {
                                log.warn("无法转换检验结果值: {}", resultValue);
                            }
                        }

                        labReports.add(processedData);
                    });

                    log.info("自定义处理器处理了 {} 条检验报告", labReports.size());
                    return labReports;
                }

                @Override
                public boolean supports(String queryKey) {
                    return "lab_report".equals(queryKey);
                }

                @Override
                public QueryType getQueryType(String queryKey) {
                    return QueryType.ONE_TO_MANY;
                }
            };
        }

        /**
         * 自定义检查报告处理器
         */
        @Bean("examReportHandler")
        public DataHandler examReportHandler() {
            return new AbstractDataHandler() {
                @Override
                public Object handleData(String queryKey, QueryContext context) {
                    if (!"exam_report".equals(queryKey)) {
                        return null;
                    }

                    log.info("使用自定义检查报告处理器处理查询");

                    Map<String, Object> mainData = context.getMainData();
                    QueryContext.QueryExecutor executor = context.getQueryExecutor();

                    List<Map<String, Object>> examReports = new ArrayList<>();
                    Map<String, Object> params = context.getDynamicParams();

                    if (params == null) {
                        params = new HashMap<>();
                    }

                    if (mainData != null && mainData.containsKey("patient_id")) {
                        params.put("patientId", mainData.get("patient_id"));
                    }

                    executor.streamQuery(queryKey, params, data -> {
                        // 自定义处理：添加英文项目名
                        Map<String, Object> processedData = new HashMap<>(data);
                        
                        String examItem = getSafeString(data, "exam_item");
                        String examItemEn = translateExamItem(examItem);
                        
                        processedData.put("exam_item_en", examItemEn);
                        processedData.put("processed_time", new java.util.Date());
                        processedData.put("processed_by", "ExamReportHandler");

                        examReports.add(processedData);
                    });

                    log.info("自定义处理器处理了 {} 条检查报告", examReports.size());
                    return examReports;
                }

                @Override
                public boolean supports(String queryKey) {
                    return "exam_report".equals(queryKey);
                }

                @Override
                public QueryType getQueryType(String queryKey) {
                    return QueryType.ONE_TO_MANY;
                }

                /**
                 * 检查项目名称翻译（简单示例）
                 */
                private String translateExamItem(String chineseName) {
                    if (chineseName == null) return "";

                    Map<String, String> translations = new HashMap<>();
                    translations.put("胸部CT", "Chest CT");
                    translations.put("腹部B超", "Abdominal Ultrasound");
                    translations.put("腰椎MRI", "Lumbar Spine MRI");
                    translations.put("颈椎X线", "Cervical Spine X-ray");
                    translations.put("腰椎CT", "Lumbar Spine CT");
                    translations.put("骨密度检查", "Bone Mineral Density");
                    translations.put("心脏彩超", "Cardiac Ultrasound");
                    translations.put("心电图", "Electrocardiogram");

                    return translations.getOrDefault(chineseName, chineseName);
                }
            };
        }
    }
}

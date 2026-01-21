package database;

import com.github.leyland.letool.data.DatabaseApplication;
import com.github.leyland.letool.data.database.DatabaseManager;
import com.github.leyland.letool.data.database.DatabaseQueryExecutor;
import com.github.leyland.letool.data.database.DatabaseQueryService;
import com.github.leyland.letool.data.database.DatabaseQueryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @ClassName <h2>DatabaseAutoConfigurationTest</h2>
 * @Description     Database 包自动配置测试
 * @Author leyland
 * @Date 2026/01/16
 * @Version 1.0
 **/
@Slf4j
@SpringBootTest(classes = DatabaseApplication.class)
public class DatabaseAutoConfigurationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private DatabaseManager databaseManager;

    @Autowired
    private DatabaseQueryBuilder queryBuilder;

    @Autowired
    private DatabaseQueryExecutor queryExecutor;

    @Autowired
    private DatabaseQueryService queryService;

    @Test
    @DisplayName("测试1：验证数据源自动配置")
    public void testDataSourceAutoConfiguration() {
        assertNotNull(dataSource, "数据源应该自动配置");
        log.info("数据源类型: {}", dataSource.getClass().getName());
    }

    @Test
    @DisplayName("测试2：验证 DatabaseManager 自动配置")
    public void testDatabaseManagerAutoConfiguration() {
        assertNotNull(databaseManager, "DatabaseManager 应该自动配置");
        assertTrue(databaseManager.testConnection(""), "数据源连接测试应该通过");
        log.info("DatabaseManager 配置成功");
    }

    @Test
    @DisplayName("测试3：验证 DatabaseQueryBuilder 自动配置")
    public void testDatabaseQueryBuilderAutoConfiguration() {
        assertNotNull(queryBuilder, "DatabaseQueryBuilder 应该自动配置");
        log.info("DatabaseQueryBuilder 配置成功");
    }

    @Test
    @DisplayName("测试4：验证 DatabaseQueryExecutor 自动配置")
    public void testDatabaseQueryExecutorAutoConfiguration() {
        assertNotNull(queryExecutor, "DatabaseQueryExecutor 应该自动配置");
        log.info("DatabaseQueryExecutor 配置成功");
    }

    @Test
    @DisplayName("测试5：验证 DatabaseQueryService 自动配置")
    public void testDatabaseQueryServiceAutoConfiguration() {
        assertNotNull(queryService, "DatabaseQueryService 应该自动配置");
        log.info("DatabaseQueryService 配置成功");
    }

    @Test
    @DisplayName("测试6：验证所有核心组件都已加载")
    public void testAllComponentsLoaded() {
        assertNotNull(dataSource, "DataSource 未加载");
        assertNotNull(databaseManager, "DatabaseManager 未加载");
        assertNotNull(queryBuilder, "DatabaseQueryBuilder 未加载");
        assertNotNull(queryExecutor, "DatabaseQueryExecutor 未加载");
        assertNotNull(queryService, "DatabaseQueryService 未加载");

        log.info("✅ 所有核心组件均已成功加载");
    }
}

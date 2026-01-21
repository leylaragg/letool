package com.github.leyland.letool.data.database;

import com.github.leyland.letool.data.database.builder.DatabaseQueryBuilder;
import com.github.leyland.letool.data.database.core.DatabaseConfig;
import com.github.leyland.letool.data.database.core.DatabaseManager;
import com.github.leyland.letool.data.database.core.DatabaseQueryExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @ClassName <h2>DatabaseAutoConfiguration</h2>
 * @Description     数据库模块自动配置类
 * @Author leyland
 * @Date 2026/01/16
 * @Version 1.0
 **/
@Slf4j
@Configuration
@EnableConfigurationProperties(DatabaseConfig.class)
@ConditionalOnProperty(prefix = "letool.database", name = "enabled", havingValue = "true")
public class DatabaseAutoConfiguration {

    private final DatabaseConfig config;

    public DatabaseAutoConfiguration(DatabaseConfig config) {
        this.config = config;
    }

    /**
     * 配置 DatabaseQueryExecutor
     */
    @Bean
    public DatabaseQueryExecutor databaseQueryExecutor(DatabaseManager databaseManager, DatabaseQueryBuilder queryBuilder) {
        DatabaseQueryExecutor executor = new DatabaseQueryExecutor(databaseManager, queryBuilder);
        // 注入配置
        executor.setConfig(config);
        return executor;
    }

    /**
     * 配置 DatabaseQueryBuilder
     */
    @Bean
    public DatabaseQueryBuilder databaseQueryBuilder() {
        return new DatabaseQueryBuilder();
    }

    /**
     * 配置 DatabaseManager
     */
    @Bean
    public DatabaseManager databaseManager(DatabaseConfig config) {
        return new DatabaseManager(config);
    }

    /**
     * 配置 DatabaseQueryService
     */
    @Bean
    public DatabaseQueryService databaseQueryService(DatabaseQueryExecutor queryExecutor, DataHandlerFactory handlerFactory) {
        return new DatabaseQueryService(queryExecutor, handlerFactory);
    }
}

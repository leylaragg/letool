package com.github.leyland.letool.data.database;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @ClassName <h2>DatabaseManager</h2>
 * @Description     数据库管理器
 *                      管理数据源的获取和生命周期
 * @Author leyland
 * @Date 2026/01/16
 * @Version 1.0
 **/
@Slf4j
@Component
@ConditionalOnProperty(prefix = "letool.database", name = "enabled", havingValue = "true")
public class DatabaseManager {

    /**
     * 数据源缓存：key-数据源名称, value-DataSource实例
     */
    private final Map<String, DataSource> dataSourceMap = new ConcurrentHashMap<>();

    private final DatabaseConfig config;

    private DataSource springDataSource;

    @Autowired
    public DatabaseManager(DatabaseConfig config) {
        this.config = config;
        log.info("DatabaseManager 初始化完成");
    }

    /**
     * 注入 Spring Boot 默认数据源（如果配置了使用）
     */
    @Autowired(required = false)
    public void setSpringDataSource(DataSource springDataSource) {
        if (config.getUseSpringDataSource() != null && config.getUseSpringDataSource()) {
            this.springDataSource = springDataSource;
            log.info("已注入 Spring Boot 默认数据源");
        }
    }

    /**
     * 获取指定名称的数据源
     */
    public DataSource getDataSource(String datasourceName) {
        // 如果配置了使用 Spring Boot 数据源且没有指定数据源名称
        if (config.getUseSpringDataSource() != null && config.getUseSpringDataSource()) {
            if (datasourceName == null || datasourceName.isEmpty()) {
                if (springDataSource != null) {
                    return springDataSource;
                }
                log.warn("未找到 Spring Boot 默认数据源");
            }
        }

        DataSource dataSource = dataSourceMap.get(datasourceName);
        if (dataSource == null) {
            throw new IllegalArgumentException("未找到数据源: " + datasourceName);
        }
        return dataSource;
    }

    /**
     * 注册自定义数据源
     */
    public void registerDataSource(String name, DataSource dataSource) {
        dataSourceMap.put(name, dataSource);
        log.info("注册数据源: {}", name);
    }

    /**
     * 获取所有已初始化的数据源名称
     */
    public java.util.Set<String> getDataSourceNames() {
        return dataSourceMap.keySet();
    }

    /**
     * 测试数据源连接是否正常
     */
    public boolean testConnection(String datasourceName) {
        try {
            DataSource dataSource = getDataSource(datasourceName);
            try (java.sql.Connection conn = dataSource.getConnection()) {
                return conn.isValid(5); // 5秒超时验证
            }
        } catch (Exception e) {
            log.error("测试数据源连接失败: {}", datasourceName, e);
            return false;
        }
    }
}

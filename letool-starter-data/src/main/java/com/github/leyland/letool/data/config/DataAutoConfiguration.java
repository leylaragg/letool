package com.github.leyland.letool.data.config;

import com.github.leyland.letool.data.core.LetoolTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 数据库模块自动配置类。
 *
 * <p>当 classpath 中存在 {@link JdbcTemplate}、容器中已有 {@link JdbcTemplate}
 * Bean，且配置 {@code letool.data.enabled=true}（默认为 true）时注册 JDBC adapter
 * {@link LetoolTemplate}。查询条件、分页模型等纯工具类型可脱离 Spring 直接使用。</p>
 *
 * <p>数据库方言通过 JDBC URL 自动检测，支持 MySQL 和 PostgreSQL。
 * 检测失败时默认回退为 MySQL 方言。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(DataProperties.class)
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnProperty(prefix = "letool.data", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DataAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DataAutoConfiguration.class);

    /**
     * 注册 {@link LetoolTemplate} Bean。
     *
     * <p>如果用户已手动定义 {@link LetoolTemplate}，则此默认 Bean 不会被创建。
     * 方言由 LetoolTemplate 内部通过 JDBC URL 自动检测。</p>
     *
     * @param jdbcTemplate Spring JdbcTemplate 实例
     * @param properties   数据库模块配置属性
     * @return LetoolTemplate 实例
     */
    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean
    public LetoolTemplate letoolTemplate(JdbcTemplate jdbcTemplate, DataProperties properties) {
        log.info("LetoolTemplate initialized, dialect: {}",
                jdbcTemplate.getDataSource() != null ? "auto-detect" : "unknown");
        return new LetoolTemplate(jdbcTemplate, properties);
    }
}

package com.github.leyland.letool.data.config;

import com.github.leyland.letool.data.core.LetoolTemplate;
import com.github.leyland.letool.data.dialect.MySqlDialect;
import com.github.leyland.letool.data.dialect.PostgreSqlDialect;
import com.github.leyland.letool.data.dialect.SqlDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@AutoConfiguration
@EnableConfigurationProperties(DataProperties.class)
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnProperty(prefix = "letool.data", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DataAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DataAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public LetoolTemplate letoolTemplate(JdbcTemplate jdbcTemplate, DataProperties properties) {
        log.info("LetoolTemplate initialized, dialect: {}",
                jdbcTemplate.getDataSource() != null ? "auto-detect" : "unknown");
        return new LetoolTemplate(jdbcTemplate, properties);
    }
}

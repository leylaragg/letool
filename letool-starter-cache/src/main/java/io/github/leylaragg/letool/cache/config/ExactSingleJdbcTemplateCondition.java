package io.github.leylaragg.letool.cache.config;

import org.springframework.jdbc.core.JdbcTemplate;

/** 仅存在一个 JDBC 模板时才允许创建默认 Outbox 仓储。 */
final class ExactSingleJdbcTemplateCondition extends ExactSingleBeanCondition {

    ExactSingleJdbcTemplateCondition() {
        super(JdbcTemplate.class);
    }
}

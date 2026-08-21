package io.github.leylaragg.letool.cache.config;

/** 仅存在一个 JDBC 模板时才允许创建默认 Outbox 仓储。 */
final class ExactSingleJdbcTemplateCondition extends ExactSingleBeanCondition {

    private static final String JDBC_TEMPLATE_CLASS_NAME = "org.springframework.jdbc.core.JdbcTemplate";

    /** 延迟交给基础条件解析 JdbcTemplate，保留 spring-jdbc 的可选依赖语义。 */
    ExactSingleJdbcTemplateCondition() {
        super(JDBC_TEMPLATE_CLASS_NAME);
    }
}

package com.github.leyland.letool.data.dialect;

public class PostgreSqlDialect implements SqlDialect {

    @Override
    public String buildPaginationSql(String sql, int offset, int limit) {
        return sql + " LIMIT " + limit + " OFFSET " + offset;
    }

    @Override
    public String buildCountSql(String sql) {
        return "SELECT COUNT(*) FROM (" + sql + ") _count_t";
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return "\"" + identifier + "\"";
    }

    @Override
    public String getDatabaseType() {
        return "PostgreSQL";
    }
}

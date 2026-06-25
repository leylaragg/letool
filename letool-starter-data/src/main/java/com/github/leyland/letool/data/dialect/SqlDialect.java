package com.github.leyland.letool.data.dialect;

public interface SqlDialect {

    String buildPaginationSql(String sql, int offset, int limit);

    String buildCountSql(String sql);

    default String quoteIdentifier(String identifier) {
        return "`" + identifier + "`";
    }

    String getDatabaseType();
}

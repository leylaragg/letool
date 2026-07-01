package com.github.leyland.letool.data.dialect;

/**
 * PostgreSQL 数据库方言实现。
 *
 * <p>分页使用标准 SQL {@code LIMIT n OFFSET m} 语法（与 MySQL 相同）。
 * 标识符使用双引号 {@code "} 引用以兼容大小写敏感和关键字。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public class PostgreSqlDialect implements SqlDialect {

    /**
     * 构建 PostgreSQL 分页 SQL，使用 {@code LIMIT ... OFFSET ...} 语法。
     *
     * @param sql    原始查询 SQL
     * @param offset 偏移量
     * @param limit  每页条数
     * @return 分页 SQL
     */
    @Override
    public String buildPaginationSql(String sql, int offset, int limit) {
        return sql + " LIMIT " + limit + " OFFSET " + offset;
    }

    /**
     * 构建总数查询 SQL，将子查询包装在 {@code SELECT COUNT(*) FROM (...)} 中。
     *
     * @param sql 原始查询 SQL
     * @return 总数查询 SQL
     */
    @Override
    public String buildCountSql(String sql) {
        return "SELECT COUNT(*) FROM (" + sql + ") _count_t";
    }

    /**
     * PostgreSQL 使用双引号 {@code "} 引用标识符以保留大小写。
     *
     * @param identifier 原始标识符
     * @return 双引号引用后的标识符
     */
    @Override
    public String quoteIdentifier(String identifier) {
        return "\"" + identifier + "\"";
    }

    @Override
    public String getDatabaseType() {
        return "PostgreSQL";
    }
}

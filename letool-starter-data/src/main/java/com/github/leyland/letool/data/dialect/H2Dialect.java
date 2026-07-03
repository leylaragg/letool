package com.github.leyland.letool.data.dialect;

/**
 * H2 数据库方言实现。
 *
 * <p>主要用于本地开发、单元测试和轻量级嵌入式数据库场景。H2 支持标准
 * {@code LIMIT ... OFFSET ...} 分页语法，标识符使用双引号引用。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public class H2Dialect implements SqlDialect {

    /**
     * 构建 H2 分页 SQL。
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
     * 构建总数查询 SQL。
     *
     * @param sql 原始查询 SQL
     * @return 总数查询 SQL
     */
    @Override
    public String buildCountSql(String sql) {
        return "SELECT COUNT(*) FROM (" + sql + ") _count_t";
    }

    /**
     * H2 使用双引号引用标识符。
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
        return "H2";
    }
}

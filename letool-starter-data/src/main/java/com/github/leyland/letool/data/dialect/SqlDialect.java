package com.github.leyland.letool.data.dialect;

/**
 * SQL 方言接口，定义不同数据库的分页 SQL 构建和标识符引用方式。
 *
 * <p>不同数据库的分页语法差异较大（MySQL 用 LIMIT/OFFSET、Oracle 用 ROWNUM、
 * PostgreSQL 用 LIMIT/OFFSET 等），方言接口将差异抽象为统一的方法，供
 * {@code LetoolTemplate} 在构建 SQL 时调用。</p>
 *
 * <p>内置实现：</p>
 * <ul>
 *   <li>{@link MySqlDialect} — MySQL / MariaDB</li>
 *   <li>{@link com.github.leyland.letool.data.dialect.PostgreSqlDialect} — PostgreSQL</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 */
public interface SqlDialect {

    /**
     * 构建分页 SQL。
     *
     * <p>在原始 SQL 后追加分页子句，不同数据库实现不同。</p>
     *
     * @param sql    原始查询 SQL
     * @param offset 偏移量（从 0 开始）
     * @param limit  每页条数
     * @return 带分页子句的完整 SQL
     */
    String buildPaginationSql(String sql, int offset, int limit);

    /**
     * 构建总数查询 SQL。
     *
     * <p>将原始查询 SQL 包装为 SELECT COUNT(*) 形式。</p>
     *
     * @param sql 原始查询 SQL
     * @return 总数查询 SQL
     */
    String buildCountSql(String sql);

    /**
     * 引用数据库标识符（表名、列名），防止与 SQL 关键字冲突。
     *
     * <p>默认使用反引号（MySQL 风格），子类可覆写。</p>
     *
     * @param identifier 原始标识符
     * @return 引用后的标识符
     */
    default String quoteIdentifier(String identifier) {
        return "`" + identifier + "`";
    }

    /**
     * 获取数据库类型标识。
     *
     * @return 数据库类型字符串（如 "MySQL"、"PostgreSQL"）
     */
    String getDatabaseType();
}

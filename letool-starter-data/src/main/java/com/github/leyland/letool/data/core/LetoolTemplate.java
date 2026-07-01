package com.github.leyland.letool.data.core;

import com.github.leyland.letool.data.annotation.Column;
import com.github.leyland.letool.data.annotation.Id;
import com.github.leyland.letool.data.annotation.Table;
import com.github.leyland.letool.data.annotation.Transient;
import com.github.leyland.letool.data.config.DataProperties;
import com.github.leyland.letool.data.dialect.MySqlDialect;
import com.github.leyland.letool.data.dialect.SqlDialect;
import com.github.leyland.letool.data.exception.DataException;
import com.github.leyland.letool.data.mapper.BeanPropertyRowMapper;
import com.github.leyland.letool.data.query.Condition;
import com.github.leyland.letool.data.query.LambdaQuery;
import com.github.leyland.letool.data.query.Sort;
import com.github.leyland.letool.tool.util.StrUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据库操作核心模板，封装 Spring {@link JdbcTemplate} 提供更便捷的 CRUD 操作。
 *
 * <p>相比直接使用 JdbcTemplate，LetoolTemplate 提供以下增强：</p>
 * <ul>
 *   <li><b>Lambda 查询</b> — 类型安全的查询构造器，通过方法引用而非字符串指定列名</li>
 *   <li><b>注解驱动映射</b> — {@link Table @Table}、{@link Column @Column}、
 *       {@link Id @Id}、{@link Transient @Transient} 注解声明映射关系</li>
 *   <li><b>自动分页</b> — 内置分页查询和方言适配（MySQL / PostgreSQL）</li>
 *   <li><b>自增主键回填</b> — INSERT 后自动将数据库生成的 ID 回填到实体</li>
 *   <li><b>类型安全</b> — 泛型化 API，编译期类型检查</li>
 * </ul>
 *
 * <p>数据库方言在构造时通过 JDBC URL 自动检测：</p>
 * <ul>
 *   <li>URL 包含 {@code mysql} → {@link MySqlDialect}</li>
 *   <li>URL 包含 {@code postgresql} → {@link com.github.leyland.letool.data.dialect.PostgreSqlDialect}</li>
 *   <li>检测失败 → 默认回退为 MySQL 方言</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 */
public class LetoolTemplate {

    private static final Logger log = LoggerFactory.getLogger(LetoolTemplate.class);

    private final JdbcTemplate jdbcTemplate;
    private final DataProperties properties;
    private final SqlDialect dialect;

    /**
     * 构造 LetoolTemplate 实例并自动检测数据库方言。
     *
     * @param jdbcTemplate Spring JdbcTemplate 实例
     * @param properties   数据库模块配置属性
     */
    public LetoolTemplate(JdbcTemplate jdbcTemplate, DataProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.dialect = detectDialect(jdbcTemplate);
    }

    /**
     * 通过 JDBC URL 检测数据库类型，返回对应的方言实例。
     *
     * <p>检测逻辑基于 URL 中的关键字匹配。如果获取连接失败则默认使用 MySQL 方言。</p>
     *
     * @param jt JdbcTemplate 实例
     * @return 匹配的方言实例，检测失败时默认返回 MySqlDialect
     */
    private SqlDialect detectDialect(JdbcTemplate jt) {
        try {
            String url = jt.getDataSource().getConnection().getMetaData().getURL();
            if (url.contains("mysql")) return new MySqlDialect();
            if (url.contains("postgresql")) return new com.github.leyland.letool.data.dialect.PostgreSqlDialect();
        } catch (Exception e) {
            log.debug("Failed to detect database dialect, defaulting to MySQL", e);
        }
        return new MySqlDialect();
    }

    // ======================== Lambda Query ========================

    /**
     * 创建类型安全的 Lambda 查询构造器。
     *
     * <p>通过方法引用构建查询条件，避免手写列名字符串。</p>
     *
     * <pre>{@code
     * letoolTemplate.lambdaQuery(User.class)
     *     .eq(User::getStatus, 1)
     *     .like(User::getName, "%张三%")
     *     .orderByDesc(User::getCreateTime)
     *     .list();
     * }</pre>
     *
     * @param entityClass 实体类
     * @param <T>         实体类型
     * @return Lambda 查询构造器
     */
    public <T> LambdaQuery<T> lambdaQuery(Class<T> entityClass) {
        return new LambdaQuery<>(entityClass);
    }

    // ======================== Select ========================

    /**
     * 根据主键 ID 查询单条记录。
     *
     * <p>表名由实体类的 {@link Table @Table} 注解或类名自动推导，
     * 主键列由 {@link Id @Id} 注解或默认字段名 "id" 确定。</p>
     *
     * @param entityClass 实体类
     * @param id          主键值
     * @param <T>         实体类型
     * @return 查询到的实体，未找到时返回 {@code null}
     */
    public <T> T selectById(Class<T> entityClass, Object id) {
        String tableName = resolveTableName(entityClass);
        String idColumn = resolveIdColumn(entityClass);
        String sql = "SELECT * FROM " + tableName + " WHERE " + idColumn + " = ?";
        List<T> result = jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(entityClass, properties.getMapping().isAutoCamelCase()), id);
        return result.isEmpty() ? null : result.get(0);
    }

    /**
     * 执行 Lambda 查询，返回结果列表。
     *
     * <p>根据 LambdaQuery 中的条件自动构建 SQL 并执行查询。
     * 结果通过 {@link BeanPropertyRowMapper} 映射为实体对象。</p>
     *
     * @param query Lambda 查询构造器
     * @param <T>   实体类型
     * @return 查询结果列表，无结果时返回空列表
     */
    public <T> List<T> selectList(LambdaQuery<T> query) {
        String sql = buildSelectSql(query);
        Object[] params = buildParams(query.getConditions());
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(query.getEntityClass(), properties.getMapping().isAutoCamelCase()), params);
    }

    /**
     * 执行 Lambda 查询，返回单条结果。
     *
     * <p>无结果时返回 {@code null}；返回多条记录时抛出 {@link DataException}。</p>
     *
     * @param query Lambda 查询构造器
     * @param <T>   实体类型
     * @return 单条查询结果，无结果时返回 {@code null}
     * @throws DataException 如果结果集包含多条记录
     */
    public <T> T selectOne(LambdaQuery<T> query) {
        List<T> list = selectList(query);
        if (list.isEmpty()) return null;
        if (list.size() > 1) {
            throw new DataException("DATA_001", "Expected 1 result, got " + list.size());
        }
        return list.get(0);
    }

    /**
     * 执行计数查询，返回符合条件的记录总数。
     *
     * @param query Lambda 查询构造器（只使用 WHERE 条件，忽略 SELECT 列和排序）
     * @param <T>   实体类型
     * @return 记录总数
     */
    public <T> long selectCount(LambdaQuery<T> query) {
        String sql = buildCountSql(query);
        Object[] params = buildParams(query.getConditions());
        Long count = jdbcTemplate.queryForObject(sql, Long.class, params);
        return count != null ? count : 0L;
    }

    /**
     * 执行分页查询。
     *
     * <p>先执行 COUNT 获取总数，再执行分页查询获取当前页数据。
     * 分页参数受 {@link DataProperties.Pagination#getMaxPageSize()} 限制，
     * 超过上限时自动截断。</p>
     *
     * @param query    Lambda 查询构造器
     * @param page     当前页码（从 1 开始）
     * @param pageSize 每页大小
     * @param <T>      实体类型
     * @return 分页结果，包含数据列表和分页元信息
     */
    public <T> PaginationResult<T> selectPage(LambdaQuery<T> query, int page, int pageSize) {
        int maxPageSize = properties.getPagination().getMaxPageSize();
        if (pageSize > maxPageSize) {
            pageSize = maxPageSize;
        }
        int offset = (page - 1) * pageSize;
        long total = selectCount(query);
        String sql = buildSelectSql(query);
        String pageSql = dialect.buildPaginationSql(sql, offset, pageSize);
        Object[] params = buildParams(query.getConditions());
        List<T> records = jdbcTemplate.query(pageSql, new BeanPropertyRowMapper<>(query.getEntityClass(), properties.getMapping().isAutoCamelCase()), params);
        return new PaginationResult<>(records, total, page, pageSize);
    }

    // ======================== Insert ========================

    /**
     * 插入一条记录。
     *
     * <p>自动扫描实体类的非瞬态字段构造 INSERT 语句。如果实体类包含自增主键
     * （{@link Id @Id} 标记且类型为 Long/Integer），且配置
     * {@code useGeneratedKeys=true}，则在插入后自动回填数据库生成的 ID。</p>
     *
     * @param entity 要插入的实体对象
     * @param <T>    实体类型
     * @return 影响行数（通常为 1）
     * @throws DataException 如果实体没有可插入的字段
     */
    public <T> int insert(T entity) {
        String tableName = resolveTableName(entity.getClass());
        Map<String, Object> fieldValues = extractFieldValues(entity, false);
        if (fieldValues.isEmpty()) {
            throw new DataException("DATA_002", "No fields to insert for " + entity.getClass().getName());
        }
        String columns = String.join(", ", fieldValues.keySet());
        String placeholders = fieldValues.keySet().stream().map(k -> "?").collect(Collectors.joining(", "));
        String sql = "INSERT INTO " + tableName + " (" + columns + ") VALUES (" + placeholders + ")";

        if (properties.getMapping().isUseGeneratedKeys() && hasAutoIncrementId(entity.getClass())) {
            GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                int i = 1;
                for (Object val : fieldValues.values()) {
                    ps.setObject(i++, val);
                }
                return ps;
            }, keyHolder);
            Number key = keyHolder.getKey();
            if (key != null) {
                setIdValue(entity, key.longValue());
            }
            return 1;
        }
        return jdbcTemplate.update(sql, fieldValues.values().toArray());
    }

    // ======================== Update ========================

    /**
     * 根据主键更新一条记录。
     *
     * <p>自动构建 {@code UPDATE ... SET ... WHERE id = ?} 语句。
     * 只更新非主键、非瞬态字段。如果实体没有可更新的非主键字段，直接返回 0。</p>
     *
     * @param entity 要更新的实体对象（必须包含非 null 的主键值）
     * @param <T>    实体类型
     * @return 影响行数
     * @throws DataException 如果主键值为 null
     */
    public <T> int update(T entity) {
        String tableName = resolveTableName(entity.getClass());
        String idColumn = resolveIdColumn(entity.getClass());
        Object idValue = getIdValue(entity);
        if (idValue == null) {
            throw new DataException("DATA_003", "Cannot update entity with null ID");
        }
        Map<String, Object> fieldValues = extractFieldValues(entity, true);
        if (fieldValues.isEmpty()) {
            return 0;
        }
        String setClause = fieldValues.keySet().stream()
                .map(col -> col + " = ?").collect(Collectors.joining(", "));
        String sql = "UPDATE " + tableName + " SET " + setClause + " WHERE " + idColumn + " = ?";
        List<Object> params = new ArrayList<>(fieldValues.values());
        params.add(idValue);
        return jdbcTemplate.update(sql, params.toArray());
    }

    // ======================== Delete ========================

    /**
     * 根据主键删除一条记录。
     *
     * @param entityClass 实体类
     * @param id          主键值
     * @param <T>         实体类型
     * @return 影响行数
     */
    public <T> int deleteById(Class<T> entityClass, Object id) {
        String tableName = resolveTableName(entityClass);
        String idColumn = resolveIdColumn(entityClass);
        return jdbcTemplate.update("DELETE FROM " + tableName + " WHERE " + idColumn + " = ?", id);
    }

    // ======================== Raw SQL ========================

    /**
     * 执行原始 SQL 查询，使用自定义 RowMapper 映射结果。
     *
     * @param sql        原始 SQL 语句
     * @param rowMapper  结果映射器
     * @param params     查询参数
     * @param <T>        结果类型
     * @return 查询结果列表
     */
    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... params) {
        return jdbcTemplate.query(sql, rowMapper, params);
    }

    /**
     * 执行原始 SQL 查询，返回简单类型列表。
     *
     * @param sql         原始 SQL 语句
     * @param elementType 结果元素类型（如 String、Integer）
     * @param params      查询参数
     * @param <T>         结果类型
     * @return 查询结果列表
     */
    public <T> List<T> queryForList(String sql, Class<T> elementType, Object... params) {
        return jdbcTemplate.queryForList(sql, elementType, params);
    }

    /**
     * 执行原始 SQL DML 语句（INSERT、UPDATE、DELETE）。
     *
     * @param sql    原始 SQL 语句
     * @param params 参数
     * @return 影响行数
     */
    public int execute(String sql, Object... params) {
        return jdbcTemplate.update(sql, params);
    }

    /** @return 底层的 Spring JdbcTemplate 实例 */
    public JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    /** @return 当前使用的数据库方言 */
    public SqlDialect getDialect() {
        return dialect;
    }

    // ======================== SQL 构建 ========================

    /**
     * 根据 LambdaQuery 构建 SELECT SQL 语句。
     *
     * <p>依次拼接 SELECT 列、FROM 表名、WHERE 条件和 ORDER BY 子句。</p>
     */
    private String buildSelectSql(LambdaQuery<?> query) {
        String tableName = query.getTableName() != null ? query.getTableName() : resolveTableName(query.getEntityClass());
        String columns = query.getSelectColumns().isEmpty() ? "*" : String.join(", ", query.getSelectColumns());
        StringBuilder sb = new StringBuilder("SELECT ").append(columns).append(" FROM ").append(tableName);
        appendWhereClause(sb, query.getConditions());
        appendOrderBy(sb, query.getSort());
        return sb.toString();
    }

    /** 根据 LambdaQuery 构建 SELECT COUNT(*) SQL 语句。 */
    private String buildCountSql(LambdaQuery<?> query) {
        String tableName = query.getTableName() != null ? query.getTableName() : resolveTableName(query.getEntityClass());
        StringBuilder sb = new StringBuilder("SELECT COUNT(*) FROM ").append(tableName);
        appendWhereClause(sb, query.getConditions());
        return sb.toString();
    }

    /** 将 WHERE 条件列表追加到 SQL StringBuilder。 */
    private void appendWhereClause(StringBuilder sb, List<Condition> conditions) {
        if (conditions.isEmpty()) return;
        sb.append(" WHERE ");
        for (int i = 0; i < conditions.size(); i++) {
            if (i > 0) sb.append(" AND ");
            Condition c = conditions.get(i);
            sb.append(c.getColumn());
            switch (c.getOp()) {
                case EQ: sb.append(" = ?"); break;
                case NE: sb.append(" <> ?"); break;
                case GT: sb.append(" > ?"); break;
                case GE: sb.append(" >= ?"); break;
                case LT: sb.append(" < ?"); break;
                case LE: sb.append(" <= ?"); break;
                case LIKE: sb.append(" LIKE ?"); break;
                case NOT_LIKE: sb.append(" NOT LIKE ?"); break;
                case IN:
                    @SuppressWarnings("unchecked")
                    List<Object> inList = (List<Object>) c.getValue();
                    sb.append(" IN (");
                    sb.append(inList.stream().map(v -> "?").collect(Collectors.joining(", ")));
                    sb.append(")");
                    break;
                case NOT_IN:
                    @SuppressWarnings("unchecked")
                    List<Object> notInList = (List<Object>) c.getValue();
                    sb.append(" NOT IN (");
                    sb.append(notInList.stream().map(v -> "?").collect(Collectors.joining(", ")));
                    sb.append(")");
                    break;
                case BETWEEN: sb.append(" BETWEEN ? AND ?"); break;
                case IS_NULL: sb.append(" IS NULL"); break;
                case IS_NOT_NULL: sb.append(" IS NOT NULL"); break;
            }
        }
    }

    /** 将 ORDER BY 子句追加到 SQL StringBuilder。 */
    private void appendOrderBy(StringBuilder sb, Sort sort) {
        if (sort == null || sort.isEmpty()) return;
        sb.append(" ORDER BY ");
        List<Sort.Order> orders = sort.getOrders();
        for (int i = 0; i < orders.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(orders.get(i).getColumn()).append(" ").append(orders.get(i).getDirection().name());
        }
    }

    /**
     * 从 WHERE 条件列表中提取 PreparedStatement 参数值数组。
     *
     * <p>IS_NULL / IS_NOT_NULL 不占参数位；IN / NOT_IN 展开为多个占位符；
     * BETWEEN 占两个参数位；其他运算符占一个。</p>
     */
    private Object[] buildParams(List<Condition> conditions) {
        List<Object> params = new ArrayList<>();
        for (Condition c : conditions) {
            switch (c.getOp()) {
                case IS_NULL:
                case IS_NOT_NULL:
                    break;
                case IN:
                case NOT_IN:
                    @SuppressWarnings("unchecked")
                    List<Object> list = (List<Object>) c.getValue();
                    params.addAll(list);
                    break;
                case BETWEEN:
                    params.add(c.getValue());
                    params.add(c.getValue2());
                    break;
                default:
                    params.add(c.getValue());
                    break;
            }
        }
        return params.toArray();
    }

    // ======================== 实体元数据解析 ========================

    /**
     * 解析实体类对应的数据库表名。
     *
     * <p>优先使用 {@link Table @Table} 注解指定的名称，
     * 否则将类名按驼峰转下划线规则自动推导。</p>
     */
    private String resolveTableName(Class<?> clazz) {
        Table tableAnn = clazz.getAnnotation(Table.class);
        if (tableAnn != null) {
            return tableAnn.value();
        }
        return StrUtil.toSnakeCase(clazz.getSimpleName());
    }

    /**
     * 解析实体类的主键列名。
     *
     * <p>查找标记 {@link Id @Id} 的字段，如果该字段同时有 {@link Column @Column} 注解
     * 则使用注解值，否则按驼峰转下划线推导。未找到 @Id 字段时默认返回 "id"。</p>
     */
    private String resolveIdColumn(Class<?> clazz) {
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Id.class)) {
                Column colAnn = field.getAnnotation(Column.class);
                if (colAnn != null) return colAnn.value();
                return StrUtil.toSnakeCase(field.getName());
            }
        }
        return "id";
    }

    /** 判断实体类的主键是否为自增数字类型（Long / Integer 及其基本类型）。 */
    private boolean hasAutoIncrementId(Class<?> clazz) {
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Id.class)) {
                return field.getType() == Long.class || field.getType() == long.class
                        || field.getType() == Integer.class || field.getType() == int.class;
            }
        }
        return false;
    }

    /** 通过反射获取实体的主键值。先找 @Id 注解字段，未找到则尝试字段名 "id"。 */
    private Object getIdValue(Object entity) {
        try {
            for (Field field : entity.getClass().getDeclaredFields()) {
                if (field.isAnnotationPresent(Id.class)) {
                    field.setAccessible(true);
                    return field.get(entity);
                }
            }
            Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            return idField.get(entity);
        } catch (Exception e) {
            return null;
        }
    }

    /** INSERT 后回填数据库生成的自增主键值到实体对象。 */
    private void setIdValue(Object entity, long idValue) {
        try {
            for (Field field : entity.getClass().getDeclaredFields()) {
                if (field.isAnnotationPresent(Id.class)) {
                    field.setAccessible(true);
                    if (field.getType() == Long.class || field.getType() == long.class) {
                        field.set(entity, idValue);
                    } else if (field.getType() == Integer.class || field.getType() == int.class) {
                        field.set(entity, (int) idValue);
                    }
                    return;
                }
            }
            Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, idValue);
        } catch (Exception e) {
            log.debug("Failed to set ID value: {}", e.getMessage());
        }
    }

    /**
     * 提取实体的字段名-值映射，用于构造 INSERT/UPDATE 的列和参数。
     *
     * <p>跳过标记 {@link Transient @Transient} 的字段和静态字段。
     * 当 {@code skipId=true} 时跳过主键字段（用于 UPDATE 场景）。</p>
     *
     * @param entity 实体对象
     * @param skipId 是否跳过主键字段
     * @return 列名 → 参数值的有序映射
     */
    private Map<String, Object> extractFieldValues(Object entity, boolean skipId) {
        Map<String, Object> values = new LinkedHashMap<>();
        try {
            for (Field field : entity.getClass().getDeclaredFields()) {
                if (field.isAnnotationPresent(Transient.class)) continue;
                if (Modifier.isStatic(field.getModifiers())) continue;
                if (skipId && field.isAnnotationPresent(Id.class)) continue;

                field.setAccessible(true);
                Object value = field.get(entity);
                String columnName;
                Column colAnn = field.getAnnotation(Column.class);
                if (colAnn != null) {
                    columnName = colAnn.value();
                } else {
                    columnName = StrUtil.toSnakeCase(field.getName());
                }
                values.put(columnName, value);
            }
        } catch (Exception e) {
            throw new DataException("DATA_004", "Failed to extract field values", e);
        }
        return values;
    }
}

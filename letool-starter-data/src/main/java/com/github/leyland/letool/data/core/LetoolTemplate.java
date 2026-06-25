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

public class LetoolTemplate {

    private static final Logger log = LoggerFactory.getLogger(LetoolTemplate.class);

    private final JdbcTemplate jdbcTemplate;
    private final DataProperties properties;
    private final SqlDialect dialect;

    public LetoolTemplate(JdbcTemplate jdbcTemplate, DataProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.dialect = detectDialect(jdbcTemplate);
    }

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

    public <T> LambdaQuery<T> lambdaQuery(Class<T> entityClass) {
        return new LambdaQuery<>(entityClass);
    }

    // ======================== Select ========================

    public <T> T selectById(Class<T> entityClass, Object id) {
        String tableName = resolveTableName(entityClass);
        String idColumn = resolveIdColumn(entityClass);
        String sql = "SELECT * FROM " + tableName + " WHERE " + idColumn + " = ?";
        List<T> result = jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(entityClass, properties.getMapping().isAutoCamelCase()), id);
        return result.isEmpty() ? null : result.get(0);
    }

    public <T> List<T> selectList(LambdaQuery<T> query) {
        String sql = buildSelectSql(query);
        Object[] params = buildParams(query.getConditions());
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(query.getEntityClass(), properties.getMapping().isAutoCamelCase()), params);
    }

    public <T> T selectOne(LambdaQuery<T> query) {
        List<T> list = selectList(query);
        if (list.isEmpty()) return null;
        if (list.size() > 1) {
            throw new DataException("DATA_001", "Expected 1 result, got " + list.size());
        }
        return list.get(0);
    }

    public <T> long selectCount(LambdaQuery<T> query) {
        String sql = buildCountSql(query);
        Object[] params = buildParams(query.getConditions());
        Long count = jdbcTemplate.queryForObject(sql, Long.class, params);
        return count != null ? count : 0L;
    }

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

    public <T> int deleteById(Class<T> entityClass, Object id) {
        String tableName = resolveTableName(entityClass);
        String idColumn = resolveIdColumn(entityClass);
        return jdbcTemplate.update("DELETE FROM " + tableName + " WHERE " + idColumn + " = ?", id);
    }

    // ======================== Raw SQL ========================

    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... params) {
        return jdbcTemplate.query(sql, rowMapper, params);
    }

    public <T> List<T> queryForList(String sql, Class<T> elementType, Object... params) {
        return jdbcTemplate.queryForList(sql, elementType, params);
    }

    public int execute(String sql, Object... params) {
        return jdbcTemplate.update(sql, params);
    }

    public JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    public SqlDialect getDialect() {
        return dialect;
    }

    // ======================== Internal helpers ========================

    private String buildSelectSql(LambdaQuery<?> query) {
        String tableName = query.getTableName() != null ? query.getTableName() : resolveTableName(query.getEntityClass());
        String columns = query.getSelectColumns().isEmpty() ? "*" : String.join(", ", query.getSelectColumns());
        StringBuilder sb = new StringBuilder("SELECT ").append(columns).append(" FROM ").append(tableName);
        appendWhereClause(sb, query.getConditions());
        appendOrderBy(sb, query.getSort());
        return sb.toString();
    }

    private String buildCountSql(LambdaQuery<?> query) {
        String tableName = query.getTableName() != null ? query.getTableName() : resolveTableName(query.getEntityClass());
        StringBuilder sb = new StringBuilder("SELECT COUNT(*) FROM ").append(tableName);
        appendWhereClause(sb, query.getConditions());
        return sb.toString();
    }

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

    private void appendOrderBy(StringBuilder sb, Sort sort) {
        if (sort == null || sort.isEmpty()) return;
        sb.append(" ORDER BY ");
        List<Sort.Order> orders = sort.getOrders();
        for (int i = 0; i < orders.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(orders.get(i).getColumn()).append(" ").append(orders.get(i).getDirection().name());
        }
    }

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

    private String resolveTableName(Class<?> clazz) {
        Table tableAnn = clazz.getAnnotation(Table.class);
        if (tableAnn != null) {
            return tableAnn.value();
        }
        return StrUtil.toSnakeCase(clazz.getSimpleName());
    }

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

    private boolean hasAutoIncrementId(Class<?> clazz) {
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Id.class)) {
                return field.getType() == Long.class || field.getType() == long.class
                        || field.getType() == Integer.class || field.getType() == int.class;
            }
        }
        return false;
    }

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

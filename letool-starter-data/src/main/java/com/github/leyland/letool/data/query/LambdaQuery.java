package com.github.leyland.letool.data.query;

import com.github.leyland.letool.data.core.PaginationResult;
import com.github.leyland.letool.tool.util.StrUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class LambdaQuery<T> {

    private final Class<T> entityClass;
    private final List<Condition> conditions = new ArrayList<>();
    private Sort sort;
    private String tableName;
    private final List<String> selectColumns = new ArrayList<>();

    public LambdaQuery(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    public LambdaQuery<T> table(String tableName) {
        this.tableName = tableName;
        return this;
    }

    public LambdaQuery<T> select(String... columns) {
        for (String col : columns) {
            selectColumns.add(col);
        }
        return this;
    }

    public LambdaQuery<T> eq(SFunction<T, ?> column, Object value) {
        if (value != null) {
            conditions.add(new Condition(resolveColumn(column), Condition.Op.EQ, value));
        }
        return this;
    }

    public LambdaQuery<T> ne(SFunction<T, ?> column, Object value) {
        if (value != null) {
            conditions.add(new Condition(resolveColumn(column), Condition.Op.NE, value));
        }
        return this;
    }

    public LambdaQuery<T> gt(SFunction<T, ?> column, Object value) {
        if (value != null) {
            conditions.add(new Condition(resolveColumn(column), Condition.Op.GT, value));
        }
        return this;
    }

    public LambdaQuery<T> ge(SFunction<T, ?> column, Object value) {
        if (value != null) {
            conditions.add(new Condition(resolveColumn(column), Condition.Op.GE, value));
        }
        return this;
    }

    public LambdaQuery<T> lt(SFunction<T, ?> column, Object value) {
        if (value != null) {
            conditions.add(new Condition(resolveColumn(column), Condition.Op.LT, value));
        }
        return this;
    }

    public LambdaQuery<T> le(SFunction<T, ?> column, Object value) {
        if (value != null) {
            conditions.add(new Condition(resolveColumn(column), Condition.Op.LE, value));
        }
        return this;
    }

    public LambdaQuery<T> like(SFunction<T, ?> column, String value) {
        if (value != null) {
            conditions.add(new Condition(resolveColumn(column), Condition.Op.LIKE, "%" + value + "%"));
        }
        return this;
    }

    public LambdaQuery<T> notLike(SFunction<T, ?> column, String value) {
        if (value != null) {
            conditions.add(new Condition(resolveColumn(column), Condition.Op.NOT_LIKE, "%" + value + "%"));
        }
        return this;
    }

    public LambdaQuery<T> in(SFunction<T, ?> column, List<?> values) {
        if (values != null && !values.isEmpty()) {
            conditions.add(new Condition(resolveColumn(column), Condition.Op.IN, values));
        }
        return this;
    }

    public LambdaQuery<T> notIn(SFunction<T, ?> column, List<?> values) {
        if (values != null && !values.isEmpty()) {
            conditions.add(new Condition(resolveColumn(column), Condition.Op.NOT_IN, values));
        }
        return this;
    }

    public LambdaQuery<T> between(SFunction<T, ?> column, Object start, Object end) {
        if (start != null && end != null) {
            conditions.add(new Condition(resolveColumn(column), Condition.Op.BETWEEN, start, end));
        }
        return this;
    }

    public LambdaQuery<T> isNull(SFunction<T, ?> column) {
        conditions.add(new Condition(resolveColumn(column), Condition.Op.IS_NULL, null));
        return this;
    }

    public LambdaQuery<T> isNotNull(SFunction<T, ?> column) {
        conditions.add(new Condition(resolveColumn(column), Condition.Op.IS_NOT_NULL, null));
        return this;
    }

    public LambdaQuery<T> orderByAsc(SFunction<T, ?> column) {
        if (sort == null) sort = new Sort();
        sort.asc(resolveColumn(column));
        return this;
    }

    public LambdaQuery<T> orderByDesc(SFunction<T, ?> column) {
        if (sort == null) sort = new Sort();
        sort.desc(resolveColumn(column));
        return this;
    }

    public List<Condition> getConditions() { return conditions; }
    public Sort getSort() { return sort; }
    public Class<T> getEntityClass() { return entityClass; }
    public String getTableName() { return tableName; }
    public List<String> getSelectColumns() { return selectColumns; }

    @FunctionalInterface
    public interface SFunction<T, R> extends java.util.function.Function<T, R>, java.io.Serializable {}

    private String resolveColumn(SFunction<T, ?> func) {
        String implName = func.getClass().getDeclaredMethods()[0].getName();
        if (implName.startsWith("get")) {
            implName = implName.substring(3);
        } else if (implName.startsWith("is")) {
            implName = implName.substring(2);
        }
        return StrUtil.toSnakeCase(implName);
    }
}

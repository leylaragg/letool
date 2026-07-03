package com.github.leyland.letool.data.query;

import com.github.leyland.letool.data.annotation.Column;
import com.github.leyland.letool.data.core.PaginationResult;
import com.github.leyland.letool.tool.util.StrUtil;

import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 类型安全的 Lambda 查询构造器，通过方法引用构建 WHERE 条件和排序。
 *
 * <p>无需手写列名字符串，利用 Java Lambda 方法引用自动解析属性名并转换为数据库列名。
 * 所有条件方法（{@code eq}、{@code like} 等）在传入 {@code null} 值时自动跳过该条件，
 * 方便动态查询场景。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * letoolTemplate.lambdaQuery(User.class)
 *     .eq(User::getStatus, 1)
 *     .like(User::getName, "张三")
 *     .between(User::getCreateTime, start, end)
 *     .orderByDesc(User::getCreateTime)
 *     .list();
 * }</pre>
 *
 * <p>Lambda 表达式通过序列化机制解析：框架对 Lambda 进行序列化写入和回读，
 * 提取 {@code writeReplace} 方法返回的 {@code SerializedLambda} 中的方法名，
 * 再由方法名（getXxx / isXxx）推导出属性名，最后通过 {@link StrUtil#toSnakeCase}
 * 转换为数据库列名。</p>
 *
 * @param <T> 实体类型
 * @author leyland
 * @since 2.0.0
 */
public class LambdaQuery<T> {

    private final Class<T> entityClass;
    private final List<Condition> conditions = new ArrayList<>();
    private Sort sort;
    private String tableName;
    private final List<String> selectColumns = new ArrayList<>();

    /**
     * 构造 LambdaQuery 实例。
     *
     * @param entityClass 实体类
     */
    public LambdaQuery(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    /**
     * 指定自定义表名，覆盖实体类注解或自动推导的表名。
     *
     * @param tableName 自定义表名
     * @return this
     */
    public LambdaQuery<T> table(String tableName) {
        this.tableName = tableName;
        return this;
    }

    /**
     * 指定要查询的列，未调用时默认为 SELECT *。
     *
     * @param columns 列名数组
     * @return this
     */
    public LambdaQuery<T> select(String... columns) {
        for (String col : columns) {
            selectColumns.add(col);
        }
        return this;
    }

    // ======================== 比较条件 ========================

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

    // ======================== 模糊匹配 ========================

    /**
     * 添加 LIKE 条件，自动在值两侧添加 {@code %} 通配符。
     *
     * @param column 字段引用（方法引用）
     * @param value  匹配值（不需手动加 %）
     * @return this
     */
    public LambdaQuery<T> like(SFunction<T, ?> column, String value) {
        if (value != null) {
            conditions.add(new Condition(resolveColumn(column), Condition.Op.LIKE, "%" + value + "%"));
        }
        return this;
    }

    /**
     * 添加 NOT LIKE 条件，自动在值两侧添加 {@code %} 通配符。
     */
    public LambdaQuery<T> notLike(SFunction<T, ?> column, String value) {
        if (value != null) {
            conditions.add(new Condition(resolveColumn(column), Condition.Op.NOT_LIKE, "%" + value + "%"));
        }
        return this;
    }

    // ======================== 集合条件 ========================

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

    // ======================== 区间条件 ========================

    public LambdaQuery<T> between(SFunction<T, ?> column, Object start, Object end) {
        if (start != null && end != null) {
            conditions.add(new Condition(resolveColumn(column), Condition.Op.BETWEEN, start, end));
        }
        return this;
    }

    // ======================== NULL 条件 ========================

    public LambdaQuery<T> isNull(SFunction<T, ?> column) {
        conditions.add(new Condition(resolveColumn(column), Condition.Op.IS_NULL, null));
        return this;
    }

    public LambdaQuery<T> isNotNull(SFunction<T, ?> column) {
        conditions.add(new Condition(resolveColumn(column), Condition.Op.IS_NOT_NULL, null));
        return this;
    }

    // ======================== 排序 ========================

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

    // ======================== Getter ========================

    public List<Condition> getConditions() { return conditions; }
    public Sort getSort() { return sort; }
    public Class<T> getEntityClass() { return entityClass; }
    public String getTableName() { return tableName; }
    public List<String> getSelectColumns() { return selectColumns; }

    /**
     * 可序列化的函数式接口，用于接收 getter 方法引用并解析属性名。
     *
     * <p>继承 {@link java.util.function.Function} 和 {@link java.io.Serializable}，
     * Lambda 表达式在序列化时通过 {@code writeReplace} 暴露方法元数据。</p>
     *
     * @param <T> 实体类型
     * @param <R> getter 返回值类型
     */
    @FunctionalInterface
    public interface SFunction<T, R> extends java.util.function.Function<T, R>, java.io.Serializable {}

    /**
     * 从 Lambda 方法引用解析对应的数据库列名。
     *
     * <p>解析流程：</p>
     * <ol>
     *   <li>获取 Lambda 实现类的第一个方法名（即 SerializedLambda 中的 implMethodName）</li>
     *   <li>如果以 {@code get} 开头，去掉前 3 个字符；如果以 {@code is} 开头，去掉前 2 个字符</li>
     *   <li>将剩余部分用 {@link StrUtil#toSnakeCase} 转换为下划线命名</li>
     * </ol>
     *
     * @param func Lambda 方法引用
     * @return 对应的数据库列名（snake_case）
     */
    private String resolveColumn(SFunction<T, ?> func) {
        String propertyName = resolvePropertyName(func);
        try {
            Field field = entityClass.getDeclaredField(propertyName);
            Column column = field.getAnnotation(Column.class);
            if (column != null) {
                return column.value();
            }
        } catch (NoSuchFieldException ignored) {
            // Fallback to naming convention below.
        }
        return StrUtil.toSnakeCase(propertyName);
    }

    /**
     * 从可序列化方法引用中解析 Java 属性名。
     *
     * @param func getter 方法引用
     * @return Java 属性名
     */
    private String resolvePropertyName(SFunction<T, ?> func) {
        String implName = resolveImplMethodName(func);
        if (implName.startsWith("get")) {
            implName = implName.substring(3);
        } else if (implName.startsWith("is")) {
            implName = implName.substring(2);
        }
        if (implName.isEmpty()) {
            return implName;
        }
        return Character.toLowerCase(implName.charAt(0)) + implName.substring(1);
    }

    /**
     * 读取 Lambda 序列化元数据中的实现方法名。
     *
     * @param func getter 方法引用
     * @return 实现方法名，如 {@code getUserName}
     */
    private String resolveImplMethodName(SFunction<T, ?> func) {
        try {
            Method writeReplace = func.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            SerializedLambda lambda = (SerializedLambda) writeReplace.invoke(func);
            return lambda.getImplMethodName();
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("无法解析 Lambda 字段引用", e);
        }
    }
}

package com.github.leyland.letool.data.query;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL ORDER BY 排序模型，支持多列排序的链式构建。
 *
 * <p>通过 {@link #asc(String)} 和 {@link #desc(String)} 方法链式添加排序字段，
 * 添加顺序即 SQL 中的排序优先级。</p>
 *
 * <pre>{@code
 * Sort sort = new Sort()
 *     .desc("create_time")
 *     .asc("id");
 * // 生成: ORDER BY create_time DESC, id ASC
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class Sort {

    /** 排序字段列表，按添加顺序排列 */
    private final List<Order> orders = new ArrayList<>();

    /**
     * 添加升序排列字段。
     *
     * @param column 列名
     * @return this，支持链式调用
     */
    public Sort asc(String column) {
        orders.add(new Order(column, Direction.ASC));
        return this;
    }

    /**
     * 添加降序排列字段。
     *
     * @param column 列名
     * @return this，支持链式调用
     */
    public Sort desc(String column) {
        orders.add(new Order(column, Direction.DESC));
        return this;
    }

    public List<Order> getOrders() { return orders; }

    /**
     * 判断排序列表是否为空。
     *
     * @return {@code true} 如果没有添加任何排序字段
     */
    public boolean isEmpty() { return orders.isEmpty(); }

    /**
     * 单个排序字段的定义：列名 + 方向。
     */
    public static class Order {
        /** 列名 */
        private String column;
        /** 排序方向 */
        private Direction direction;

        public Order() {}
        public Order(String column, Direction direction) { this.column = column; this.direction = direction; }

        public String getColumn() { return column; }
        public Direction getDirection() { return direction; }
    }

    /** 排序方向：升序 / 降序 */
    public enum Direction { ASC, DESC }
}

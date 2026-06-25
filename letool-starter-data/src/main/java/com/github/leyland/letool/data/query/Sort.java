package com.github.leyland.letool.data.query;

import java.util.ArrayList;
import java.util.List;

public class Sort {

    private final List<Order> orders = new ArrayList<>();

    public Sort asc(String column) {
        orders.add(new Order(column, Direction.ASC));
        return this;
    }

    public Sort desc(String column) {
        orders.add(new Order(column, Direction.DESC));
        return this;
    }

    public List<Order> getOrders() { return orders; }

    public boolean isEmpty() { return orders.isEmpty(); }

    public static class Order {
        private String column;
        private Direction direction;

        public Order() {}
        public Order(String column, Direction direction) { this.column = column; this.direction = direction; }

        public String getColumn() { return column; }
        public Direction getDirection() { return direction; }
    }

    public enum Direction { ASC, DESC }
}

package com.github.leyland.letool.data.query;

import java.util.ArrayList;
import java.util.List;

public class Condition {

    private String column;
    private Op op;
    private Object value;
    private Object value2;

    public Condition() {}

    public Condition(String column, Op op, Object value) {
        this.column = column;
        this.op = op;
        this.value = value;
    }

    public Condition(String column, Op op, Object value, Object value2) {
        this.column = column;
        this.op = op;
        this.value = value;
        this.value2 = value2;
    }

    public String getColumn() { return column; }
    public Op getOp() { return op; }
    public Object getValue() { return value; }
    public Object getValue2() { return value2; }

    public enum Op {
        EQ, NE, GT, GE, LT, LE, LIKE, NOT_LIKE, IN, NOT_IN, BETWEEN, IS_NULL, IS_NOT_NULL
    }
}

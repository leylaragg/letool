package com.github.leyland.letool.data.query;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL WHERE 条件模型，封装单个查询条件的列名、运算符和参数值。
 *
 * <p>由 {@link LambdaQuery} 的链式方法（{@code eq}、{@code like}、{@code between} 等）
 * 内部创建，传递给 {@code LetoolTemplate} 用于构建 SQL。</p>
 *
 * <p>部分运算符需要两个值（如 {@code BETWEEN} 需要 start 和 end），
 * 此时使用 {@link #getValue2()} 获取第二个值。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public class Condition {

    /** 数据库列名 */
    private String column;

    /** 运算符 */
    private Op op;

    /** 第一个参数值（或 IN/NOT_IN 的值列表） */
    private Object value;

    /** 第二个参数值（仅 BETWEEN 使用） */
    private Object value2;

    /** 无参构造 */
    public Condition() {}

    /**
     * 构造单值条件（EQ、NE、GT、LIKE 等）。
     *
     * @param column 列名
     * @param op     运算符
     * @param value  参数值
     */
    public Condition(String column, Op op, Object value) {
        this.column = column;
        this.op = op;
        this.value = value;
    }

    /**
     * 构造双值条件（BETWEEN）。
     *
     * @param column 列名
     * @param op     运算符
     * @param value  起始值
     * @param value2 结束值
     */
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

    /**
     * SQL 条件运算符枚举。
     */
    public enum Op {
        /** 等于 */       EQ,
        /** 不等于 */     NE,
        /** 大于 */       GT,
        /** 大于等于 */   GE,
        /** 小于 */       LT,
        /** 小于等于 */   LE,
        /** 模糊匹配 */   LIKE,
        /** 不模糊匹配 */ NOT_LIKE,
        /** 在列表中 */   IN,
        /** 不在列表中 */ NOT_IN,
        /** 区间查询 */   BETWEEN,
        /** 为空 */       IS_NULL,
        /** 不为空 */     IS_NOT_NULL
    }
}

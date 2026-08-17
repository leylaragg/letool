package io.github.leylaragg.letool.excel.validation;

import java.util.ArrayList;
import java.util.List;

/**
 * Excel 数据校验结果。
 *
 * <p>结果对象同时记录已校验的数据行数和全部字段错误。通过
 * {@link #getErrors()} 返回的是不可修改快照，调用方不能绕过
 * {@link #addError(int, String, String)} 修改内部状态。</p>
 */
public class ValidationResult {

    /** 当前结果中收集到的字段错误。 */
    private final List<ValidationError> errors = new ArrayList<>();

    /** 已参与校验的数据行总数。 */
    private int totalRows;

    /**
     * 添加一条字段校验错误。
     *
     * @param row Excel 中的实际行号，从 1 开始
     * @param field 校验失败的字段名，不允许为空白
     * @param message 错误描述，不允许为空白
     * @throws IllegalArgumentException 当参数不符合约束时抛出
     */
    public void addError(int row, String field, String message) {
        if (row < 1) {
            throw new IllegalArgumentException("row must be greater than zero");
        }
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("field must not be blank");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        errors.add(new ValidationError(row, field, message));
    }

    /**
     * 判断是否存在字段校验错误。
     *
     * @return 至少存在一条错误时返回 {@code true}
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * 记录一行已参与校验的数据。
     */
    public void recordRow() {
        totalRows++;
    }

    /**
     * 获取已参与校验的数据行总数。
     *
     * @return 已校验行数
     */
    public int getTotalRows() {
        return totalRows;
    }

    /**
     * 合并另一个校验结果。
     *
     * @param other 待合并结果，不允许为 {@code null}
     * @throws IllegalArgumentException 当结果为空时抛出
     */
    public void merge(ValidationResult other) {
        if (other == null) {
            throw new IllegalArgumentException("other must not be null");
        }
        errors.addAll(other.errors);
        totalRows += other.totalRows;
    }

    /**
     * 获取校验错误的不可修改快照。
     *
     * @return 与当前状态隔离的不可修改错误列表
     */
    public List<ValidationError> getErrors() {
        return List.copyOf(errors);
    }

    /**
     * 单个字段的校验错误。
     */
    public static final class ValidationError {

        /** Excel 中的实际行号。 */
        private final int row;

        /** 校验失败的字段名。 */
        private final String field;

        /** 面向调用方的错误描述。 */
        private final String message;

        /**
         * 创建一条不可变校验错误。
         *
         * @param row Excel 中的实际行号
         * @param field 校验失败的字段名
         * @param message 错误描述
         */
        public ValidationError(int row, String field, String message) {
            this.row = row;
            this.field = field;
            this.message = message;
        }

        /**
         * 获取 Excel 实际行号。
         *
         * @return 实际行号
         */
        public int getRow() {
            return row;
        }

        /**
         * 获取校验失败的字段名。
         *
         * @return 字段名
         */
        public String getField() {
            return field;
        }

        /**
         * 获取错误描述。
         *
         * @return 错误描述
         */
        public String getMessage() {
            return message;
        }
    }
}

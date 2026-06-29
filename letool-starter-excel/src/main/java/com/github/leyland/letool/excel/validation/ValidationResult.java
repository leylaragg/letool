package com.github.leyland.letool.excel.validation;

import java.util.ArrayList;
import java.util.List;

/**
 * Excel数据校验结果容器。
 *
 * <p>用于在Excel导入过程中收集和查询数据校验产生的错误信息。
 * 内部维护一个 {@link ValidationError} 列表，每条错误记录
 * 包含出错行号、字段名和错误描述。
 *
 * <p><b>使用模式：</b>
 * <pre>{@code
 * ValidationResult result = new ValidationResult();
 * result.addError(3, "username", "用户名不能为空");
 * if (result.hasErrors()) {
 *     for (ValidationResult.ValidationError err : result.getErrors()) {
 *         log.warn("第{}行 {}: {}", err.getRow(), err.getField(), err.getMessage());
 *     }
 * }
 * }</pre>
 *
 * @author leyland
 * @since 1.0.0
 */
public class ValidationResult {

    // ======================== 错误列表 ========================

    /**
     * 存储所有校验错误的列表。
     */
    private final List<ValidationError> errors = new ArrayList<>();

    // ======================== 添加错误 ========================

    /**
     * 添加一条校验错误记录。
     *
     * @param row     Excel中的行号（从1开始）
     * @param field   校验失败的字段名
     * @param message 错误描述信息
     */
    public void addError(int row, String field, String message) {
        errors.add(new ValidationError(row, field, message));
    }

    // ======================== 状态查询 ========================

    /**
     * 判断是否存在校验错误。
     *
     * @return 若存在至少一条错误则返回 {@code true}，否则返回 {@code false}
     */
    public boolean hasErrors() { return !errors.isEmpty(); }

    // ======================== 获取错误列表 ========================

    /**
     * 获取所有校验错误的列表。
     *
     * <p>返回内部列表的引用，调用方可以遍历但不能安全地修改。
     *
     * @return 校验错误列表，无错误时为空列表
     */
    public List<ValidationError> getErrors() { return errors; }

    // ======================== 内部类：单条校验错误 ========================

    /**
     * 单条校验错误记录。
     *
     * <p>包含出错行号、字段名和错误消息三个不可变属性。
     */
    public static class ValidationError {

        /** 出错行号（从1开始计数）。 */
        private final int row;

        /** 校验失败的字段名。 */
        private final String field;

        /** 错误描述消息。 */
        private final String message;

        /**
         * 构造一条校验错误记录。
         *
         * @param row     Excel中的行号
         * @param field   字段名
         * @param message 错误描述
         */
        public ValidationError(int row, String field, String message) {
            this.row = row;
            this.field = field;
            this.message = message;
        }

        // ======================== Getters ========================

        /** @return 出错行号 */
        public int getRow() { return row; }

        /** @return 校验失败的字段名 */
        public String getField() { return field; }

        /** @return 错误描述消息 */
        public String getMessage() { return message; }
    }
}

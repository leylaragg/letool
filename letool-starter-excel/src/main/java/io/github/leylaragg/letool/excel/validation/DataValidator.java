package io.github.leylaragg.letool.excel.validation;

import io.github.leylaragg.letool.excel.annotation.ExcelValidation;
import io.github.leylaragg.letool.excel.exception.ExcelException;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 基于 {@link ExcelValidation} 注解的 Excel 行数据校验器。
 *
 * <p>校验器会扫描实体类及其父类中声明的字段，并依次执行必填、最小长度、
 * 最大长度和正则表达式校验。普通校验不通过会记录到 {@link ValidationResult}；
 * 反射访问失败或规则本身无效等技术故障会转换为统一的 {@link ExcelException}。</p>
 *
 * <p>该类不持有共享状态，所有方法均可安全地被多个线程调用。</p>
 */
public final class DataValidator {

    /**
     * 禁止实例化静态工具类。
     */
    private DataValidator() {
    }

    /**
     * 校验一行实体数据。
     *
     * @param entity 待校验实体，不允许为 {@code null}
     * @param rowNum 实体在 Excel 中的实际行号，从 1 开始
     * @param <T> 实体类型
     * @return 包含本行校验结果的独立结果对象
     * @throws IllegalArgumentException 当实体为空或行号小于 1 时抛出
     * @throws ExcelException 当反射访问失败或校验规则无法执行时抛出
     */
    public static <T> ValidationResult validate(T entity, int rowNum) {
        if (entity == null) {
            throw new IllegalArgumentException("entity must not be null");
        }
        if (rowNum < 1) {
            throw new IllegalArgumentException("rowNum must be greater than zero");
        }

        ValidationResult result = new ValidationResult();
        result.recordRow();
        for (Field field : getAllFields(entity.getClass())) {
            ExcelValidation validation = field.getAnnotation(ExcelValidation.class);
            if (validation == null) {
                continue;
            }
            validateField(entity, rowNum, field, validation, result);
        }
        return result;
    }

    /**
     * 对单个带注解字段执行全部校验规则。
     *
     * @param entity 当前行实体
     * @param rowNum 当前 Excel 行号
     * @param field 待校验字段
     * @param validation 字段上的校验规则
     * @param result 用于收集错误的结果对象
     */
    private static void validateField(
            Object entity,
            int rowNum,
            Field field,
            ExcelValidation validation,
            ValidationResult result) {
        try {
            if (!field.trySetAccessible()) {
                throw new IllegalAccessException("无法访问字段：" + field.getName());
            }
            Object value = field.get(entity);
            String text = value == null ? null : value.toString();

            if (text == null || text.isBlank()) {
                if (validation.required()) {
                    result.addError(
                            rowNum,
                            field.getName(),
                            resolveMessage(validation.message(), field.getName() + " 不能为空")
                    );
                }
                // 空值只由必填规则负责，避免继续产生长度或格式级联错误。
                return;
            }
            if (validation.minLength() >= 0
                    && text.length() < validation.minLength()) {
                result.addError(
                        rowNum,
                        field.getName(),
                        resolveMessage(
                                validation.message(),
                                "长度不能小于 " + validation.minLength()
                        )
                );
            }
            if (validation.maxLength() >= 0
                    && text.length() > validation.maxLength()) {
                result.addError(
                        rowNum,
                        field.getName(),
                        resolveMessage(
                                validation.message(),
                                "长度不能大于 " + validation.maxLength()
                        )
                );
            }
            if (!validation.regex().isEmpty()
                    && !Pattern.matches(validation.regex(), text)) {
                result.addError(
                        rowNum,
                        field.getName(),
                        resolveMessage(validation.message(), "格式不符合要求")
                );
            }
        } catch (IllegalAccessException | RuntimeException exception) {
            throw ExcelException.validationFailed(exception);
        }
    }

    /**
     * 优先使用注解中的自定义消息，未配置时回退到默认消息。
     *
     * @param configuredMessage 注解中配置的消息
     * @param defaultMessage 默认消息
     * @return 最终使用的错误消息
     */
    private static String resolveMessage(String configuredMessage, String defaultMessage) {
        return configuredMessage == null || configuredMessage.isBlank()
                ? defaultMessage
                : configuredMessage;
    }

    /**
     * 获取指定类及其全部父类中声明的字段。
     *
     * @param type 待扫描类型
     * @return 按子类到父类顺序收集的字段列表
     */
    private static List<Field> getAllFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> currentType = type;
        while (currentType != null && currentType != Object.class) {
            for (Field field : currentType.getDeclaredFields()) {
                fields.add(field);
            }
            currentType = currentType.getSuperclass();
        }
        return fields;
    }
}

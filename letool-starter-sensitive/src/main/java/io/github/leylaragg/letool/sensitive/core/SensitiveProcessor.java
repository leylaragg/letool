package io.github.leylaragg.letool.sensitive.core;

import io.github.leylaragg.letool.sensitive.annotation.Sensitive;
import io.github.leylaragg.letool.sensitive.exception.SensitiveException;

/**
 * 基于策略注册表执行单值脱敏的处理器。
 *
 * <p>处理器不维护全局可变状态。策略缺失或执行失败时会抛出结构化异常，
 * 不会以原始明文作为降级结果。</p>
 */
public final class SensitiveProcessor {

    private final SensitiveStrategyRegistry registry;

    /**
     * 创建脱敏处理器。
     *
     * @param registry 不可变策略注册表
     */
    public SensitiveProcessor(SensitiveStrategyRegistry registry) {
        if (registry == null) {
            throw SensitiveException.configurationInvalid("策略注册表不能为空");
        }
        this.registry = registry;
    }

    /**
     * 按字段注解执行脱敏。
     *
     * @param value 原始字符串，可为 {@code null}
     * @param annotation 字段脱敏注解
     * @return 脱敏结果；空值保持不变
     */
    public String mask(String value, Sensitive annotation) {
        if (annotation == null) {
            throw SensitiveException.configurationInvalid("Sensitive 注解不能为空");
        }
        return mask(value, annotation.type(), MaskContext.from(annotation));
    }

    /**
     * 使用策略默认参数执行脱敏。
     *
     * @param value 原始字符串，可为 {@code null}
     * @param type 脱敏类型
     * @return 脱敏结果；空值保持不变
     */
    public String mask(String value, SensitiveType type) {
        return mask(value, type, MaskContext.DEFAULT);
    }

    /**
     * 使用指定上下文执行脱敏。
     *
     * @param value 原始字符串，可为 {@code null}
     * @param type 脱敏类型
     * @param context 脱敏上下文；为 {@code null} 时使用默认上下文
     * @return 脱敏结果；空值保持不变
     */
    public String mask(String value, SensitiveType type, MaskContext context) {
        SensitiveStrategy<MaskContext> strategy = registry.getRequired(type);
        if (value == null || value.isEmpty()) {
            return value;
        }
        MaskContext effectiveContext = context == null ? MaskContext.DEFAULT : context;
        try {
            return strategy.mask(value, effectiveContext);
        } catch (SensitiveException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw SensitiveException.maskFailed(type, exception);
        }
    }

    /**
     * 获取当前处理器使用的不可变注册表。
     *
     * @return 策略注册表
     */
    public SensitiveStrategyRegistry getRegistry() {
        return registry;
    }
}

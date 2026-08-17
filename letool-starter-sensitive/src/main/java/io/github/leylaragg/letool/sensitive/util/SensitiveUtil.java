package io.github.leylaragg.letool.sensitive.util;

import io.github.leylaragg.letool.sensitive.core.MaskContext;
import io.github.leylaragg.letool.sensitive.core.SensitiveProcessor;
import io.github.leylaragg.letool.sensitive.core.SensitiveStrategyRegistry;
import io.github.leylaragg.letool.sensitive.core.SensitiveType;

/**
 * 使用内置策略执行单值脱敏的静态工具类。
 *
 * <p>Spring 应用需要使用自定义策略时，应注入 {@link SensitiveProcessor}；
 * 本工具类始终使用不可变的默认策略注册表，避免运行期全局状态污染。</p>
 */
public final class SensitiveUtil {

    private static final SensitiveProcessor DEFAULT_PROCESSOR =
            new SensitiveProcessor(SensitiveStrategyRegistry.defaults());

    /**
     * 禁止创建工具类实例。
     */
    private SensitiveUtil() {
    }

    /**
     * 使用策略默认参数执行单值脱敏。
     *
     * @param value 原始字符串，可为 {@code null}
     * @param type 脱敏类型
     * @return 脱敏结果；空值保持不变
     */
    public static String mask(String value, SensitiveType type) {
        return DEFAULT_PROCESSOR.mask(value, type);
    }

    /**
     * 使用指定上下文执行单值脱敏。
     *
     * @param value 原始字符串，可为 {@code null}
     * @param type 脱敏类型
     * @param context 脱敏上下文；为 {@code null} 时使用默认上下文
     * @return 脱敏结果；空值保持不变
     */
    public static String mask(String value, SensitiveType type, MaskContext context) {
        return DEFAULT_PROCESSOR.mask(value, type, context);
    }
}

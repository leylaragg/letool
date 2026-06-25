package com.github.leyland.letool.sensitive.util;

import com.github.leyland.letool.sensitive.annotation.Sensitive;
import com.github.leyland.letool.sensitive.core.MaskContext;
import com.github.leyland.letool.sensitive.core.SensitiveProcessor;
import com.github.leyland.letool.sensitive.core.SensitiveType;

import java.lang.reflect.Field;

/**
 * 脱敏工具类 —— 静态便捷方法入口，内部委托给 {@link SensitiveProcessor}.
 *
 * <pre>{@code
 * // 按类型脱敏（使用默认参数）
 * SensitiveUtil.mask("13812345678", SensitiveType.PHONE);  // → "138****5678"
 *
 * // 按类型脱敏（自定义 context）
 * MaskContext ctx = new MaskContext().setKeepPrefix(2).setKeepSuffix(2);
 * SensitiveUtil.mask("13812345678", SensitiveType.PHONE, ctx);  // → "13******78"
 *
 * // 反射扫描对象中所有 @Sensitive 字段并脱敏
 * User masked = SensitiveUtil.mask(user);
 *
 * // 判断字段是否需要脱敏
 * boolean need = SensitiveUtil.isSensitiveField(field);
 * }</pre>
 */
public final class SensitiveUtil {

    private SensitiveUtil() {}

    /**
     * 按类型脱敏 —— 使用策略内置的默认参数（keepPrefix/keepSuffix/maskChar）.
     *
     * @param value 待脱敏的原始字符串，为 null 时返回 null
     * @param type  脱敏类型，决定使用哪种内置策略
     * @return 脱敏后的字符串
     */
    public static String mask(String value, SensitiveType type) {
        return SensitiveProcessor.mask(value, type);
    }

    /**
     * 按类型脱敏 —— 通过 MaskContext 自定义脱敏参数.
     *
     * @param value   待脱敏的原始字符串，为 null 时返回 null
     * @param type    脱敏类型
     * @param context 自定义脱敏参数（保留长度、遮盖字符、正则等），可为 null 使用默认值
     * @return 脱敏后的字符串
     */
    public static String mask(String value, SensitiveType type, MaskContext context) {
        return SensitiveProcessor.mask(value, type, context);
    }

    /**
     * 反射扫描对象 —— 遍历所有字段，对标注 {@code @Sensitive} 的 String 字段执行脱敏.
     *
     * <p>内部通过反射创建新实例并拷贝属性，不会修改原对象.</p>
     *
     * @param object 待脱敏的对象，为 null 时返回 null
     * @return 脱敏后的新对象（与原对象不是同一引用）
     */
    public static <T> T mask(T object) {
        return SensitiveProcessor.mask(object);
    }

    /**
     * 判断字段是否标注 {@code @Sensitive} 注解.
     *
     * @param field Java 反射 Field 对象
     * @return true=该字段需要脱敏
     */
    public static boolean isSensitiveField(Field field) {
        return field.isAnnotationPresent(Sensitive.class);
    }
}

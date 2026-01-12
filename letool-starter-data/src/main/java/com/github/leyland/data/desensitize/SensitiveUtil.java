package com.github.leyland.data.desensitize;

/**
 * 敏感数据注解处理工具类
 * 用于处理 @Sensitive 注解的脱敏逻辑
 *
 * @author leyland
 * @date 2025-01-12
 */
public class SensitiveUtil {

    /**
     * 根据注解进行脱敏
     *
     * @param value 原始值
     * @param annotation 脱敏注解
     * @return 脱敏后的值
     */
    public static String desensitize(String value, Sensitive annotation) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        SensitiveType type = annotation.value();

        switch (type) {
            case CHINESE_NAME:
                return DesensitizeUtil.maskChineseName(value);

            case ID_CARD:
                return DesensitizeUtil.maskIdCard(value);

            case MOBILE_PHONE:
                return DesensitizeUtil.maskPhone(value);

            case ADDRESS:
                return DesensitizeUtil.maskAddress(value);

            case EMAIL:
                return DesensitizeUtil.maskEmail(value);

            case BANK_CARD:
                return DesensitizeUtil.maskBankCard(value);

            case PASSWORD:
                return DesensitizeUtil.maskPassword(value);

            case CUSTOM_SLIDE:
                return DesensitizeUtil.maskBySlide(
                    value,
                    annotation.leftKeep(),
                    annotation.rightKeep(),
                    annotation.maskString(),
                    annotation.reverse()
                );

            case CUSTOM_REGEX:
                String regex = annotation.regex();
                String replacement = annotation.replacement();
                if (regex != null && !regex.isEmpty()) {
                    return DesensitizeUtil.maskByRegex(value, regex, replacement);
                }
                break;

            case CUSTOM_INDEX:
                String[] indexRules = annotation.indexRules();
                if (indexRules != null && indexRules.length > 0) {
                    return DesensitizeUtil.maskByIndex(
                        value,
                        annotation.maskString().charAt(0),
                        annotation.reverse(),
                        indexRules
                    );
                }
                break;

            case DEFAULT:
            default:
                return DesensitizeUtil.maskBySlide(value, 3, 4);
        }

        return value;
    }
}

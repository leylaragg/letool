package com.github.leyland.letool.data.desensitize.handler;

/**
 * 手机号脱敏处理器。 脱敏时保留特殊字符，如空格、-、（）等。 对于手机号纯数字位数小于等于 8 的号码，保留第一个数字和最后两个数字，其他用 * 替换。
 * 对于手机号纯数字位数大于 8 的号码，保留前三位和后四位，其他用 * 替换。 eg. - 13845351234 => 138****1234 - 138-4535-1234
 * => 138-****-123 - 1-4535-34 => 1-****-*23
 *
 * @author leyland
 * @date 2025-01-12
 */
public class PhoneNumberDesensitizationHandler implements SimpleDesensitizeHandler {

    @Override
    public String mask(String origin) {
        if (origin == null) {
            return null;
        }

        StringBuilder result = new StringBuilder();
        int numberCount = 0;

        for (char c : origin.toCharArray()) {
            if (Character.isDigit(c)) {
                numberCount++;
            }
        }

        int firstDigitsToKeep;
        int lastDigitsToKeep;
        if (numberCount <= 8) {
            firstDigitsToKeep = 1;
            lastDigitsToKeep = 2;
        }
        else {
            firstDigitsToKeep = 3;
            lastDigitsToKeep = 4;
        }

        int digitCount = 0;
        for (char c : origin.toCharArray()) {
            if (Character.isDigit(c)) {
                digitCount++;
                if (digitCount <= firstDigitsToKeep || digitCount > numberCount - lastDigitsToKeep) {
                    result.append(c);
                }
                else {
                    result.append("*");
                }
            }
            else {
                result.append(c);
            }
        }

        return result.toString();
    }
}

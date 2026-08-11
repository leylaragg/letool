package com.github.leyland.letool.tool.util;

import com.github.leyland.letool.tool.random.RandomOperationException;

import java.security.SecureRandom;

/**
 * 基于 {@link SecureRandom} 的安全随机工具。
 *
 * <p>整数和长整数范围使用闭区间，浮点数范围使用左闭右开区间。
 * 本工具适合生成验证码、令牌组成片段和普通安全随机值；密码学密钥仍应使用
 * 对应密码算法的专用密钥生成器。</p>
 */
public final class RandomUtil {

    /** 线程安全的密码学安全随机源。 */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 十进制数字字符表。 */
    private static final String NUMBERS = "0123456789";

    /** 大小写英文字母字符表。 */
    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    /** 数字与大小写英文字母字符表。 */
    private static final String NUMBERS_AND_LETTERS = NUMBERS + LETTERS;

    /**
     * 禁止创建工具类实例。
     */
    private RandomUtil() {
    }

    /**
     * 生成指定闭区间内的随机整数。
     *
     * @param min 最小值，包含该值
     * @param max 最大值，包含该值
     * @return 位于 {@code [min, max]} 的随机整数
     * @throws RandomOperationException 当最小值大于最大值时抛出
     */
    public static int nextInt(int min, int max) {
        requireOrdered(min, max, "min/max");
        return (int) nextLongInclusive(min, max);
    }

    /**
     * 生成从零开始的有界随机整数。
     *
     * @param bound 上界，不包含该值且必须大于零
     * @return 位于 {@code [0, bound)} 的随机整数
     * @throws RandomOperationException 当上界不大于零时抛出
     */
    public static int nextInt(int bound) {
        if (bound <= 0) {
            throw RandomOperationException.invalidRange("bound");
        }
        return RANDOM.nextInt(bound);
    }

    /**
     * 生成指定闭区间内的随机长整数。
     *
     * @param min 最小值，包含该值
     * @param max 最大值，包含该值
     * @return 位于 {@code [min, max]} 的随机长整数
     * @throws RandomOperationException 当最小值大于最大值时抛出
     */
    public static long nextLong(long min, long max) {
        requireOrdered(min, max, "min/max");
        return nextLongInclusive(min, max);
    }

    /**
     * 生成指定左闭右开区间内的随机浮点数。
     *
     * @param min 最小值，包含该值且必须为有限数
     * @param max 最大值，不包含该值且必须为有限数
     * @return 位于 {@code [min, max)} 的随机浮点数
     * @throws RandomOperationException 当边界非有限或最小值不小于最大值时抛出
     */
    public static double nextDouble(double min, double max) {
        if (!Double.isFinite(min) || !Double.isFinite(max) || min >= max) {
            throw RandomOperationException.invalidRange("min/max");
        }
        return RANDOM.nextDouble(min, max);
    }

    /**
     * 生成指定长度的纯数字随机字符串。
     *
     * @param length 目标长度，允许为零
     * @return 纯数字随机字符串
     * @throws RandomOperationException 当长度小于零时抛出
     */
    public static String randomNumbers(int length) {
        return randomString(NUMBERS, length);
    }

    /**
     * 生成指定长度的大小写字母随机字符串。
     *
     * @param length 目标长度，允许为零
     * @return 大小写字母随机字符串
     * @throws RandomOperationException 当长度小于零时抛出
     */
    public static String randomLetters(int length) {
        return randomString(LETTERS, length);
    }

    /**
     * 生成指定长度的数字和大小写字母随机字符串。
     *
     * @param length 目标长度，允许为零
     * @return 数字和大小写字母随机字符串
     * @throws RandomOperationException 当长度小于零时抛出
     */
    public static String randomString(int length) {
        return randomString(NUMBERS_AND_LETTERS, length);
    }

    /**
     * 从指定字符表中随机选取字符并组成字符串。
     *
     * @param base 候选字符表，不得为 {@code null} 或空字符串
     * @param length 目标长度，允许为零
     * @return 随机字符串
     * @throws RandomOperationException 当字符表为空或长度小于零时抛出
     */
    public static String randomString(String base, int length) {
        if (base == null || base.isEmpty()) {
            throw RandomOperationException.invalidAlphabet("base");
        }
        requireNonNegativeLength(length);
        if (length == 0) {
            return "";
        }

        StringBuilder builder = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            builder.append(base.charAt(RANDOM.nextInt(base.length())));
        }
        return builder.toString();
    }

    /**
     * 生成指定长度的纯数字验证码。
     *
     * @param digits 验证码位数，允许为零
     * @return 纯数字验证码
     * @throws RandomOperationException 当位数小于零时抛出
     */
    public static String randomCode(int digits) {
        return randomNumbers(digits);
    }

    /**
     * 生成闭区间长整数，并单独处理 {@link Long#MAX_VALUE} 上界以避免加一溢出。
     *
     * @param min 最小值，包含该值
     * @param max 最大值，包含该值
     * @return 闭区间随机长整数
     */
    private static long nextLongInclusive(long min, long max) {
        if (min == Long.MIN_VALUE && max == Long.MAX_VALUE) {
            return RANDOM.nextLong();
        }
        if (max != Long.MAX_VALUE) {
            return RANDOM.nextLong(min, max + 1);
        }

        // 上界已经无法加一，先将整个区间左移一位，生成后再安全右移回来。
        return RANDOM.nextLong(min - 1, Long.MAX_VALUE) + 1;
    }

    /**
     * 校验整数范围顺序。
     *
     * @param min 最小值
     * @param max 最大值
     * @param parameterName 安全的参数名称
     */
    private static void requireOrdered(long min, long max, String parameterName) {
        if (min > max) {
            throw RandomOperationException.invalidRange(parameterName);
        }
    }

    /**
     * 校验随机字符串长度不得为负数。
     *
     * @param length 待校验长度
     */
    private static void requireNonNegativeLength(int length) {
        if (length < 0) {
            throw RandomOperationException.invalidLength("length");
        }
    }
}

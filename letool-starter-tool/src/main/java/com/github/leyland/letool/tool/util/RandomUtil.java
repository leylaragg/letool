package com.github.leyland.letool.tool.util;

import java.security.SecureRandom;
import java.util.Random;

/**
 * 随机生成工具类——数字、字符串、校验码.
 *
 * <h3>安全说明</h3>
 * <p>使用 {@link SecureRandom} 作为底层随机源，安全性优于 {@link java.util.Random}，
 * 适合生成验证码、Token、盐值等安全敏感场景。如需更高安全性（如密码学密钥），
 * 请使用 {@link java.security.SecureRandom#getInstanceStrong()}.</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * int num = RandomUtil.nextInt(100, 999);          // 三位随机数
 * String code = RandomUtil.randomCode(6);           // 6位数字验证码
 * String str = RandomUtil.randomString(16);         // 16位随机字符串
 * String letters = RandomUtil.randomLetters(8);     // 8位随机字母
 * }</pre>
 */
public final class RandomUtil {

    private static final Random RANDOM = new SecureRandom();
    private static final String NUMBERS = "0123456789";
    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final String NUMBERS_AND_LETTERS = NUMBERS + LETTERS;

    private RandomUtil() {}

    // ======================== 数字 ========================

    /**
     * 生成 [min, max] 范围内的随机整数（闭区间）.
     *
     * @param min 最小值（包含）
     * @param max 最大值（包含）
     * @return [min, max] 范围内的随机整数
     */
    public static int nextInt(int min, int max) {
        return min + RANDOM.nextInt(max - min + 1);
    }

    /**
     * 生成 [0, bound) 范围内的随机整数.
     *
     * @param bound 上界（不包含）
     * @return [0, bound) 范围内的随机整数
     */
    public static int nextInt(int bound) {
        return RANDOM.nextInt(bound);
    }

    /**
     * 生成 [min, max] 范围内的随机长整数（闭区间）.
     *
     * @param min 最小值（包含）
     * @param max 最大值（包含）
     * @return [min, max] 范围内的随机长整数
     */
    public static long nextLong(long min, long max) {
        return min + (long) (RANDOM.nextDouble() * (max - min + 1));
    }

    /**
     * 生成 [min, max) 范围内的随机浮点数（左闭右开）.
     *
     * @param min 最小值（包含）
     * @param max 最大值（不包含）
     * @return [min, max) 范围内的随机浮点数
     */
    public static double nextDouble(double min, double max) {
        return min + RANDOM.nextDouble() * (max - min);
    }

    // ======================== 字符串 ========================

    /**
     * 生成指定长度的纯数字随机字符串.
     *
     * @param length 长度
     * @return 纯数字字符串，{@code length <= 0} 返回空字符串
     */
    public static String randomNumbers(int length) {
        return randomString(NUMBERS, length);
    }

    /**
     * 生成指定长度的纯字母随机字符串（大小写混合）.
     *
     * @param length 长度
     * @return 纯字母字符串，{@code length <= 0} 返回空字符串
     */
    public static String randomLetters(int length) {
        return randomString(LETTERS, length);
    }

    /**
     * 生成指定长度的随机字符串（数字 + 大小写字母）.
     *
     * @param length 长度
     * @return 随机字符串，{@code length <= 0} 返回空字符串
     */
    public static String randomString(int length) {
        return randomString(NUMBERS_AND_LETTERS, length);
    }

    /**
     * 从指定字符集中随机选取字符组成字符串.
     *
     * @param base   候选字符集
     * @param length 目标长度
     * @return 随机字符串，{@code length <= 0} 返回空字符串
     */
    public static String randomString(String base, int length) {
        if (length <= 0) return "";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(base.charAt(RANDOM.nextInt(base.length())));
        }
        return sb.toString();
    }

    // ======================== 校验码 ========================

    /**
     * 生成指定长度的纯数字验证码.
     *
     * @param digits 位数
     * @return 纯数字字符串（如 6 位 → "384729"）
     */
    public static String randomCode(int digits) {
        StringBuilder sb = new StringBuilder(digits);
        for (int i = 0; i < digits; i++) {
            sb.append(NUMBERS.charAt(RANDOM.nextInt(10)));
        }
        return sb.toString();
    }
}

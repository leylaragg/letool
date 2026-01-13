package com.github.leyland.letool.data.desensitize;

import com.github.leyland.letool.data.desensitize.handler.*;
import com.github.leyland.letool.data.desensitize.rule.*;

/**
 * 脱敏工具类
 * 提供便捷的脱敏方法
 *
 * @author leyland
 * @date 2025-01-12
 */
public final class DesensitizeUtil {

    private DesensitizeUtil() {
    }

    /**
     * 中文名脱敏
     * 单名：*梦
     * 双名：张*梦
     *
     * @param origin 原始值
     * @return 脱敏后的值
     */
    public static String maskChineseName(String origin) {
        ChineseNameHandler handler = new ChineseNameHandler();
        return handler.mask(origin);
    }

    /**
     * 身份证脱敏
     * 保留前6位和后4位：430123********432X
     *
     * @param origin 原始值
     * @return 脱敏后的值
     */
    public static String maskIdCard(String origin) {
        SlideDesensitizeHandler handler = DesensitizeHandlerHolder.getSlideHandler();
        return handler.mask(origin, new IdCardSlideRule());
    }

    /**
     * 手机号脱敏
     * 保留前3位和后4位：138****5678
     *
     * @param origin 原始值
     * @return 脱敏后的值
     */
    public static String maskPhone(String origin) {
        SlideDesensitizeHandler handler = DesensitizeHandlerHolder.getSlideHandler();
        return handler.mask(origin, new PhoneNumberSlideRule());
    }

    /**
     * 银行卡号脱敏
     * 保留前6位和后4位：622260********1234
     *
     * @param origin 原始值
     * @return 脱敏后的值
     */
    public static String maskBankCard(String origin) {
        SlideDesensitizeHandler handler = DesensitizeHandlerHolder.getSlideHandler();
        return handler.mask(origin, new BankCardSlideRule());
    }

    /**
     * 地址脱敏
     * 只显示到地区：北京市西城区******
     *
     * @param origin 原始值
     * @return 脱敏后的值
     */
    public static String maskAddress(String origin) {
        SlideDesensitizeHandler handler = DesensitizeHandlerHolder.getSlideHandler();
        return handler.mask(origin, new AddressSlideRule());
    }

    /**
     * 邮箱脱敏
     * 只显示第一个字符和@之后：t****@qq.com
     *
     * @param origin 原始值
     * @return 脱敏后的值
     */
    public static String maskEmail(String origin) {
        RegexDesensitizeHandler handler = DesensitizeHandlerHolder.getRegexHandler();
        return handler.mask(origin, new EmailRegexRule());
    }

    /**
     * 密码脱敏
     * 全部替换为 ******
     *
     * @param origin 原始值
     * @return 脱敏后的值
     */
    public static String maskPassword(String origin) {
        PasswordHandler handler = new PasswordHandler();
        return handler.mask(origin);
    }

    /**
     * 滑块脱敏（自定义规则）
     *
     * @param origin 原始值
     * @param leftKeep 左边保留长度
     * @param rightKeep 右边保留长度
     * @return 脱敏后的值
     */
    public static String maskBySlide(String origin, int leftKeep, int rightKeep) {
        SlideDesensitizeHandler handler = DesensitizeHandlerHolder.getSlideHandler();
        return handler.mask(origin, leftKeep, rightKeep);
    }

    /**
     * 滑块脱敏（完全自定义）
     *
     * @param origin 原始值
     * @param leftKeep 左边保留长度
     * @param rightKeep 右边保留长度
     * @param maskString 掩码字符串
     * @param reverse 是否反转
     * @return 脱敏后的值
     */
    public static String maskBySlide(String origin, int leftKeep, int rightKeep, String maskString, boolean reverse) {
        SlideDesensitizeHandler handler = DesensitizeHandlerHolder.getSlideHandler();
        return handler.mask(origin, leftKeep, rightKeep, maskString, reverse);
    }

    /**
     * 正则脱敏
     *
     * @param origin 原始值
     * @param regex 正则表达式
     * @param replacement 替换内容
     * @return 脱敏后的值
     */
    public static String maskByRegex(String origin, String regex, String replacement) {
        RegexDesensitizeHandler handler = DesensitizeHandlerHolder.getRegexHandler();
        return handler.mask(origin, regex, replacement);
    }

    /**
     * 索引脱敏
     * 支持复杂的索引规则，如 "1", "3-5", "9-"
     *
     * @param origin 原始值
     * @param indexRules 索引规则数组
     * @return 脱敏后的值
     */
    public static String maskByIndex(String origin, String... indexRules) {
        IndexDesensitizeHandler handler = DesensitizeHandlerHolder.getIndexHandler();
        return handler.mask(origin, indexRules);
    }

    /**
     * 索引脱敏（完全自定义）
     *
     * @param origin 原始值
     * @param maskChar 掩码字符
     * @param reverse 是否反转
     * @param indexRules 索引规则数组
     * @return 脱敏后的值
     */
    public static String maskByIndex(String origin, char maskChar, boolean reverse, String... indexRules) {
        IndexDesensitizeHandler handler = DesensitizeHandlerHolder.getIndexHandler();
        return handler.mask(origin, maskChar, reverse, indexRules);
    }
}

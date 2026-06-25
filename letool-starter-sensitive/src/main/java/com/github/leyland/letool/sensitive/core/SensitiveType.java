package com.github.leyland.letool.sensitive.core;

/**
 * 脱敏类型枚举 —— 定义所有内置的脱敏规则类别.
 *
 * <h3>分类</h3>
 * <ul>
 *   <li><b>个人信息</b>：PHONE, ID_CARD, NAME, EMAIL, ADDRESS, PASSWORD</li>
 *   <li><b>金融</b>：BANK_CARD</li>
 *   <li><b>交通</b>：CAR_LICENSE</li>
 *   <li><b>通讯</b>：FIXED_PHONE</li>
 *   <li><b>网络</b>：IPV4, IPV6</li>
 *   <li><b>社交</b>：WECHAT, QQ</li>
 *   <li><b>证件</b>：PASSPORT, DOM</li>
 *   <li><b>位置</b>：POSITION</li>
 *   <li><b>扩展</b>：CUSTOM, KEEP_LENGTH, TAIL_DISPLAY</li>
 * </ul>
 */
public enum SensitiveType {

    /** 手机号 → 138****5678 */
    PHONE,
    /** 身份证 → 3201**********1234 */
    ID_CARD,
    /** 中文姓名 → 张*（复姓保留: 欧阳*） */
    NAME,
    /** 邮箱 → t***@example.com */
    EMAIL,
    /** 银行卡 → 6222****1234 */
    BANK_CARD,
    /** 地址 → 北京市海淀区**** */
    ADDRESS,
    /** 密码 → ********（定长遮盖） */
    PASSWORD,
    /** 车牌号 → 京A****8 */
    CAR_LICENSE,
    /** 固话 → 010-****5678 */
    FIXED_PHONE,
    /** IPv4 → 192.168.*.* */
    IPV4,
    /** IPv6 → 2001:****:****::1 */
    IPV6,
    /** 微信号 → w****88 */
    WECHAT,
    /** QQ 号 → 12****90 */
    QQ,
    /** 护照 → E****1234 */
    PASSPORT,
    /** 军官证/港澳通行证 */
    DOM,
    /** 经纬度 → 39.9***,116.3*** */
    POSITION,
    /** 自定义正则脱敏（需配合 pattern / replacement 使用） */
    CUSTOM,
    /** 按比例遮盖（保留首尾） */
    KEEP_LENGTH,
    /** 仅展示尾部（如支付尾号） */
    TAIL_DISPLAY
}

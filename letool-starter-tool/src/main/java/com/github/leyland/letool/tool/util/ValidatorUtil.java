package com.github.leyland.letool.tool.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 提供手机号、邮箱、HTTP URL、中国大陆身份证和 IPv4 等常用格式校验。
 *
 * <p>校验方法对 {@code null} 输入返回 {@code false}。这里提供的是常见业务输入的结构校验，
 * 不替代邮箱投递验证、域名解析、身份证权威数据核验等外部真实性验证。</p>
 */
public final class ValidatorUtil {

    /** 中国大陆手机号：1 开头，第二位为 3 至 9，共 11 位数字。 */
    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");

    /** 常见 ASCII 邮箱结构，明确禁止本地部分连续点和域名标签首尾连字符。 */
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+"
                    + "(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*"
                    + "@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
                    + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$"
    );

    /** 18 位中国大陆身份证基础结构。 */
    private static final Pattern ID_CARD_18 = Pattern.compile("^\\d{17}[\\dXx]$");

    /** GB 11643 中前 17 位数字的加权因子。 */
    private static final int[] ID_CARD_WEIGHTS = {
            7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2
    };

    /** GB 11643 中加权余数对应的校验字符。 */
    private static final String ID_CARD_CHECK_CODES = "10X98765432";

    /** 邮箱地址允许的最大总长度。 */
    private static final int EMAIL_MAX_LENGTH = 254;

    /** 邮箱本地部分允许的最大长度。 */
    private static final int EMAIL_LOCAL_MAX_LENGTH = 64;

    /** 网络端口允许的最大值。 */
    private static final int MAX_PORT = 65_535;

    /**
     * 禁止创建工具类实例。
     */
    private ValidatorUtil() {
    }

    /**
     * 验证是否为中国大陆手机号。
     *
     * @param str 待验证字符串
     * @return 格式正确时返回 {@code true}
     */
    public static boolean isPhone(String str) {
        return str != null && PHONE.matcher(str).matches();
    }

    /**
     * 验证是否为常见 ASCII 邮箱格式。
     *
     * <p>除结构外还限制总长度和本地部分长度；不执行邮箱投递或域名存在性验证。</p>
     *
     * @param str 待验证字符串
     * @return 满足常见邮箱结构和长度约束时返回 {@code true}
     */
    public static boolean isEmail(String str) {
        if (str == null || str.length() > EMAIL_MAX_LENGTH) {
            return false;
        }
        int atIndex = str.lastIndexOf('@');
        if (atIndex <= 0 || atIndex > EMAIL_LOCAL_MAX_LENGTH) {
            return false;
        }
        return EMAIL.matcher(str).matches();
    }

    /**
     * 验证是否为具有合法主机和端口的 HTTP 或 HTTPS URL。
     *
     * @param str 待验证字符串
     * @return URL 语法、协议、主机和端口均合法时返回 {@code true}
     */
    public static boolean isUrl(String str) {
        if (str == null) {
            return false;
        }
        try {
            URI uri = new URI(str).parseServerAuthority();
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();
            boolean supportedScheme = scheme != null
                    && ("http".equals(scheme.toLowerCase(Locale.ROOT))
                    || "https".equals(scheme.toLowerCase(Locale.ROOT)));
            return supportedScheme
                    && host != null
                    && !host.isBlank()
                    && port <= MAX_PORT;
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    /**
     * 验证 18 位中国大陆身份证的结构、出生日期和 GB 11643 校验位。
     *
     * <p>本方法不连接权威地区码或人员信息库，因此不判断号码是否真实签发。</p>
     *
     * @param str 待验证字符串
     * @return 结构、日期和校验位均正确时返回 {@code true}
     */
    public static boolean isIdCard(String str) {
        if (str == null || !ID_CARD_18.matcher(str).matches()) {
            return false;
        }
        if (!hasValidBirthDate(str)) {
            return false;
        }
        int sum = 0;
        for (int index = 0; index < ID_CARD_WEIGHTS.length; index++) {
            sum += (str.charAt(index) - '0') * ID_CARD_WEIGHTS[index];
        }
        char expected = ID_CARD_CHECK_CODES.charAt(sum % 11);
        char actual = Character.toUpperCase(str.charAt(17));
        return expected == actual;
    }

    /**
     * 验证身份证中的出生日期是否为真实日历日期。
     *
     * @param idCard 结构已通过校验的身份证号码
     * @return 出生日期可被严格解析时返回 {@code true}
     */
    private static boolean hasValidBirthDate(String idCard) {
        try {
            LocalDate.parse(idCard.substring(6, 14), DateTimeFormatter.BASIC_ISO_DATE);
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    /**
     * 验证是否为规范十进制 IPv4 地址。
     *
     * <p>每段必须在 0 至 255 之间，除单独的 {@code 0} 外不允许前导零。</p>
     *
     * @param str 待验证字符串
     * @return 四段均满足约束时返回 {@code true}
     */
    public static boolean isIpV4(String str) {
        if (str == null) {
            return false;
        }
        String[] segments = str.split("\\.", -1);
        if (segments.length != 4) {
            return false;
        }
        for (String segment : segments) {
            if (!isValidIpV4Segment(segment)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 验证单个 IPv4 十进制分段。
     *
     * @param segment 待验证分段
     * @return 分段格式和值域均合法时返回 {@code true}
     */
    private static boolean isValidIpV4Segment(String segment) {
        if (segment.isEmpty() || segment.length() > 3) {
            return false;
        }
        if (segment.length() > 1 && segment.charAt(0) == '0') {
            return false;
        }
        int value = 0;
        for (int index = 0; index < segment.length(); index++) {
            char character = segment.charAt(index);
            if (character < '0' || character > '9') {
                return false;
            }
            value = value * 10 + character - '0';
        }
        return value <= 255;
    }

    /**
     * 判断对象是否为 {@code null}。
     *
     * @param obj 任意对象
     * @return 为 {@code null} 时返回 {@code true}
     */
    public static boolean isNull(Object obj) {
        return obj == null;
    }

    /**
     * 判断对象是否不为 {@code null}。
     *
     * @param obj 任意对象
     * @return 不为 {@code null} 时返回 {@code true}
     */
    public static boolean isNotNull(Object obj) {
        return !isNull(obj);
    }

    /**
     * 判断布尔值是否为 {@link Boolean#TRUE}。
     *
     * @param value 布尔值
     * @return 为 {@link Boolean#TRUE} 时返回 {@code true}
     */
    public static boolean isTrue(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    /**
     * 判断字符序列是否为空白。
     *
     * @param cs 待检查字符序列
     * @return 为空白时返回 {@code true}
     */
    public static boolean isBlank(CharSequence cs) {
        return StrUtil.isBlank(cs);
    }

    /**
     * 判断字符序列是否包含有效内容。
     *
     * @param cs 待检查字符序列
     * @return 包含有效内容时返回 {@code true}
     */
    public static boolean isNotBlank(CharSequence cs) {
        return StrUtil.isNotBlank(cs);
    }

    /**
     * 判断集合是否为空。
     *
     * @param coll 待检查集合
     * @return 集合为 {@code null} 或没有元素时返回 {@code true}
     */
    public static boolean isEmpty(Collection<?> coll) {
        return CollUtil.isEmpty(coll);
    }

    /**
     * 判断集合是否至少包含一个元素。
     *
     * @param coll 待检查集合
     * @return 集合非空时返回 {@code true}
     */
    public static boolean isNotEmpty(Collection<?> coll) {
        return CollUtil.isNotEmpty(coll);
    }

    /**
     * 判断字符串是否完全匹配动态正则表达式。
     *
     * <p>正则通常来自配置或规则平台，表达式语法错误时安全返回 {@code false}。</p>
     *
     * @param str 待匹配字符串
     * @param regex 动态正则表达式
     * @return 完全匹配时返回 {@code true}；参数为空或正则非法时返回 {@code false}
     */
    public static boolean matches(String str, String regex) {
        if (str == null || regex == null) {
            return false;
        }
        try {
            return Pattern.matches(regex, str);
        } catch (PatternSyntaxException exception) {
            return false;
        }
    }
}

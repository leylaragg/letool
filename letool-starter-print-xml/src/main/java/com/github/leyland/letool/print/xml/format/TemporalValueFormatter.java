package com.github.leyland.letool.print.xml.format;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 日期与日期时间共用的静态时间格式化器。
 *
 * @author leyland
 */
final class TemporalValueFormatter implements PrintValueFormatter {

    /** 时间格式化器允许的选项。 */
    private static final Set<String> OPTIONS = Set.of(
            "pattern", "input-pattern", "locale", "zone-id");

    /** 注册表名称。 */
    private final String name;

    /** 是否使用日期时间语义。 */
    private final boolean dateTime;

    /** 构造日期或日期时间格式化器。 */
    TemporalValueFormatter(String name, boolean dateTime) {
        this.name = name;
        this.dateTime = dateTime;
    }

    /** @return 注册表名称 */
    @Override
    public String name() {
        return name;
    }

    /**
     * 编译时间格式化计划。
     *
     * @param options 不可变静态选项
     * @param context 安全编译位置
     * @return 线程安全时间格式化计划
     */
    @Override
    public PrintFormatPlan compile(Map<String, String> options, FormatCompileContext context) {
        rejectUnknownOptions(options);
        Locale locale = parseLocale(options.get("locale"));
        ZoneId zone = parseZone(options.get("zone-id"));
        DateTimeFormatter input = parsePattern(options.get("input-pattern"), locale, "input-pattern");
        DateTimeFormatter output = parseOutput(options.get("pattern"), locale);
        return dateTime
                ? value -> formatDateTime(value, input, output, zone)
                : value -> formatDate(value, input, output, zone);
    }

    /** 校验未知选项。 */
    private void rejectUnknownOptions(Map<String, String> options) {
        for (String option : options.keySet()) {
            if (!OPTIONS.contains(option)) {
                throw new IllegalArgumentException(name + " 包含未知格式选项：" + option);
            }
        }
    }

    /** 解析可选 BCP 47 区域标签。 */
    private Locale parseLocale(String value) {
        if (value == null) {
            return Locale.ROOT;
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " 的 locale 不能为空白");
        }
        try {
            return new Locale.Builder().setLanguageTag(value).build();
        } catch (IllformedLocaleException exception) {
            throw new IllegalArgumentException(name + " 的 locale 不是合法 BCP 47 标签", exception);
        }
    }

    /** 解析目标时区，缺省固定使用 UTC。 */
    private ZoneId parseZone(String value) {
        if (value == null) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(value);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException(name + " 的时区不合法", exception);
        }
    }

    /** 编译可选自定义模式。 */
    private DateTimeFormatter parsePattern(String value, Locale locale, String option) {
        if (value == null) {
            return null;
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " 的 " + option + " 不能为空白");
        }
        try {
            return DateTimeFormatter.ofPattern(value, locale);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(name + " 的 " + option + " 不合法", exception);
        }
    }

    /** 创建输出格式，缺省使用稳定 ISO 格式。 */
    private DateTimeFormatter parseOutput(String value, Locale locale) {
        DateTimeFormatter formatter = parsePattern(value, locale, "pattern");
        if (formatter != null) {
            return formatter;
        }
        return dateTime ? DateTimeFormatter.ISO_OFFSET_DATE_TIME : DateTimeFormatter.ISO_LOCAL_DATE;
    }

    /** 格式化日期值。 */
    private String formatDate(
            JsonNode value, DateTimeFormatter input, DateTimeFormatter output, ZoneId zone) {
        try {
            LocalDate date;
            if (isEpochMillis(value)) {
                date = Instant.ofEpochMilli(value.longValue()).atZone(zone).toLocalDate();
            } else if (value != null && value.isTextual()) {
                date = LocalDate.parse(value.textValue(),
                        input == null ? DateTimeFormatter.ISO_LOCAL_DATE : input);
            } else {
                throw new IllegalArgumentException("date 格式化器只接受字符串或 epoch millis");
            }
            return output.format(date);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("date 输入无法按已编译格式解析", exception);
        }
    }

    /** 格式化日期时间值。 */
    private String formatDateTime(
            JsonNode value, DateTimeFormatter input, DateTimeFormatter output, ZoneId zone) {
        try {
            ZonedDateTime dateTimeValue;
            if (isEpochMillis(value)) {
                dateTimeValue = Instant.ofEpochMilli(value.longValue()).atZone(zone);
            } else if (value != null && value.isTextual()) {
                dateTimeValue = parseDateTimeText(value.textValue(), input, zone);
            } else {
                throw new IllegalArgumentException("datetime 格式化器只接受字符串或 epoch millis");
            }
            return output.format(dateTimeValue);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("datetime 输入无法按已编译格式解析", exception);
        }
    }

    /** 判断节点是否为不会截断或溢出的 epoch millis 整数。 */
    private boolean isEpochMillis(JsonNode value) {
        return value != null && value.isIntegralNumber() && value.canConvertToLong();
    }

    /** 按自定义模式或受限 ISO 形式解析日期时间文本。 */
    private ZonedDateTime parseDateTimeText(
            String value, DateTimeFormatter input, ZoneId zone) {
        if (input != null) {
            return LocalDateTime.parse(value, input).atZone(zone);
        }
        try {
            return Instant.parse(value).atZone(zone);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(value).atZoneSameInstant(zone);
            } catch (DateTimeParseException secondIgnored) {
                return LocalDateTime.parse(value).atZone(zone);
            }
        }
    }
}

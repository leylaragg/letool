package com.github.leyland.letool.sms.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.leyland.letool.sms.config.SmsProperties;
import com.github.leyland.letool.sms.exception.SmsErrorCode;
import com.github.leyland.letool.sms.exception.SmsException;
import com.github.leyland.letool.sms.model.SmsRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 有界、自动过期的单 JVM 短信发送尝试限流器。
 *
 * <p>该实现适合单实例或节点级保护。需要集群级精确限制时，应注册自定义
 * {@link SmsRateLimiter} Bean。</p>
 */
public final class LocalSmsRateLimiter implements SmsRateLimiter {

    private final SmsProperties.RateLimit properties;
    private final Clock clock;
    private final Cache<String, CounterWindow> counters;

    /**
     * 使用系统时钟创建本地限流器。
     *
     * @param properties 限流配置
     */
    public LocalSmsRateLimiter(SmsProperties.RateLimit properties) {
        this(properties, Clock.systemDefaultZone());
    }

    /**
     * 使用指定时钟创建本地限流器。
     *
     * @param properties 限流配置
     * @param clock 计算限流窗口的时钟
     */
    public LocalSmsRateLimiter(SmsProperties.RateLimit properties, Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
        validateProperties(properties);
        this.counters = Caffeine.newBuilder()
                .maximumSize(properties.getMaximumTrackedPhones())
                .expireAfterAccess(Duration.ofDays(2))
                .build();
    }

    /**
     * 检查请求内每个手机号并记录发送尝试。
     *
     * @param request 短信请求
     */
    @Override
    public void check(SmsRequest request) {
        Objects.requireNonNull(request, "request 不能为空");
        long minute = clock.instant().getEpochSecond() / 60L;
        LocalDate day = LocalDate.now(clock);
        for (String phone : request.getPhones()) {
            counters.asMap().compute(phone, (key, current) -> {
                CounterWindow window = current == null ? new CounterWindow(minute, day) : current;
                window.record(minute, day, properties);
                return window;
            });
        }
    }

    /**
     * 获取当前缓存的手机号数量，供监控和测试使用。
     *
     * @return 估算的缓存数量
     */
    public long estimatedTrackedPhones() {
        return counters.estimatedSize();
    }

    /**
     * 校验限流配置。
     *
     * @param properties 限流配置
     */
    private static void validateProperties(SmsProperties.RateLimit properties) {
        if (properties.getMaxPerMinute() <= 0) {
            throw SmsException.of(SmsErrorCode.CONFIGURATION_INVALID, "max-per-minute 必须大于 0");
        }
        if (properties.getMaxPerDay() <= 0) {
            throw SmsException.of(SmsErrorCode.CONFIGURATION_INVALID, "max-per-day 必须大于 0");
        }
        if (properties.getMaximumTrackedPhones() <= 0) {
            throw SmsException.of(SmsErrorCode.CONFIGURATION_INVALID, "maximum-tracked-phones 必须大于 0");
        }
    }

    /**
     * 单个手机号的分钟和日期窗口计数。
     */
    private static final class CounterWindow {

        private long minute;
        private LocalDate day;
        private int minuteCount;
        private int dayCount;

        /**
         * 创建空计数窗口。
         *
         * @param minute 当前分钟标识
         * @param day 当前日期
         */
        private CounterWindow(long minute, LocalDate day) {
            this.minute = minute;
            this.day = day;
        }

        /**
         * 原子检查并记录一次尝试。
         *
         * @param currentMinute 当前分钟标识
         * @param currentDay 当前日期
         * @param properties 限流配置
         */
        private synchronized void record(
                long currentMinute,
                LocalDate currentDay,
                SmsProperties.RateLimit properties) {
            if (minute != currentMinute) {
                minute = currentMinute;
                minuteCount = 0;
            }
            if (!day.equals(currentDay)) {
                day = currentDay;
                dayCount = 0;
            }
            if (minuteCount >= properties.getMaxPerMinute()) {
                throw SmsException.of(
                        SmsErrorCode.RATE_LIMITED,
                        "同一手机号每分钟最多尝试 " + properties.getMaxPerMinute() + " 次");
            }
            if (dayCount >= properties.getMaxPerDay()) {
                throw SmsException.of(
                        SmsErrorCode.RATE_LIMITED,
                        "同一手机号每天最多尝试 " + properties.getMaxPerDay() + " 次");
            }
            minuteCount++;
            dayCount++;
        }
    }
}

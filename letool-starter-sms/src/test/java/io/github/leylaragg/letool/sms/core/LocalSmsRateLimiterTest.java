package io.github.leylaragg.letool.sms.core;

import io.github.leylaragg.letool.sms.config.SmsProperties;
import io.github.leylaragg.letool.sms.exception.SmsException;
import io.github.leylaragg.letool.sms.model.SmsRequest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link LocalSmsRateLimiter} 限流窗口测试。
 */
class LocalSmsRateLimiterTest {

    /**
     * 验证同一分钟超过上限时拒绝发送尝试。
     */
    @Test
    void shouldRejectAttemptBeyondMinuteLimit() {
        SmsProperties.RateLimit properties = properties(1, 10);
        MutableClock clock = new MutableClock(Instant.parse("2026-08-05T01:00:00Z"));
        LocalSmsRateLimiter limiter = new LocalSmsRateLimiter(properties, clock);
        SmsRequest request = request();

        limiter.check(request);

        assertThatThrownBy(() -> limiter.check(request))
                .isInstanceOf(SmsException.class)
                .hasMessageContaining("每分钟");
    }

    /**
     * 验证进入下一分钟后分钟窗口会重置。
     */
    @Test
    void shouldResetMinuteWindow() {
        SmsProperties.RateLimit properties = properties(1, 10);
        MutableClock clock = new MutableClock(Instant.parse("2026-08-05T01:00:00Z"));
        LocalSmsRateLimiter limiter = new LocalSmsRateLimiter(properties, clock);
        SmsRequest request = request();
        limiter.check(request);
        clock.setInstant(Instant.parse("2026-08-05T01:01:00Z"));

        assertThatCode(() -> limiter.check(request)).doesNotThrowAnyException();
    }

    /**
     * 验证跨分钟后每日上限仍然生效。
     */
    @Test
    void shouldKeepDailyLimitAcrossMinuteWindows() {
        SmsProperties.RateLimit properties = properties(10, 1);
        MutableClock clock = new MutableClock(Instant.parse("2026-08-05T01:00:00Z"));
        LocalSmsRateLimiter limiter = new LocalSmsRateLimiter(properties, clock);
        SmsRequest request = request();
        limiter.check(request);
        clock.setInstant(Instant.parse("2026-08-05T01:01:00Z"));

        assertThatThrownBy(() -> limiter.check(request))
                .isInstanceOf(SmsException.class)
                .hasMessageContaining("每天");
    }

    /**
     * 创建测试限流配置。
     *
     * @param maxPerMinute 每分钟上限
     * @param maxPerDay 每日上限
     * @return 限流配置
     */
    private SmsProperties.RateLimit properties(int maxPerMinute, int maxPerDay) {
        SmsProperties.RateLimit properties = new SmsProperties.RateLimit();
        properties.setMaxPerMinute(maxPerMinute);
        properties.setMaxPerDay(maxPerDay);
        properties.setMaximumTrackedPhones(100);
        return properties;
    }

    /**
     * 创建固定手机号的短信请求。
     *
     * @return 短信请求
     */
    private SmsRequest request() {
        return SmsRequest.builder()
                .phone("+8613800138000")
                .templateCode("SMS_VERIFY")
                .build();
    }

    /**
     * 测试使用的可变时钟。
     */
    private static final class MutableClock extends Clock {

        private Instant instant;

        /**
         * 创建 UTC 可变时钟。
         *
         * @param instant 初始时间
         */
        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        /**
         * 修改当前时间。
         *
         * @param instant 新时间
         */
        private void setInstant(Instant instant) {
            this.instant = instant;
        }

        /**
         * 获取时区。
         *
         * @return UTC 时区
         */
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        /**
         * 创建使用其他时区的时钟。
         *
         * @param zone 目标时区
         * @return 共享当前时间的新时钟
         */
        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        /**
         * 获取当前时间。
         *
         * @return 当前时间
         */
        @Override
        public Instant instant() {
            return instant;
        }
    }
}

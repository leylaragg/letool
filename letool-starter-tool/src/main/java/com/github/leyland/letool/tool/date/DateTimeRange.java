package com.github.leyland.letool.tool.date;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 使用左闭右开语义表示不可变的本地日期时间范围。
 *
 * <p>开始时刻属于范围，结束时刻不属于范围。该语义适合直接构造数据库查询条件，
 * 不需要猜测数据库字段保存到秒、毫秒还是纳秒。</p>
 *
 * @param startInclusive 包含在范围内的开始时刻
 * @param endExclusive 不包含在范围内的结束时刻
 */
public record DateTimeRange(LocalDateTime startInclusive, LocalDateTime endExclusive) {

    /**
     * 校验范围端点完整且结束时刻严格晚于开始时刻。
     *
     * @param startInclusive 包含在范围内的开始时刻
     * @param endExclusive   不包含在范围内的结束时刻
     * @throws DateOperationException 端点为空、相等或顺序颠倒时抛出
     */
    public DateTimeRange {
        if (startInclusive == null || endExclusive == null || !endExclusive.isAfter(startInclusive)) {
            throw DateOperationException.invalidArgument("dateTimeRange");
        }
    }

    /**
     * 判断指定时刻是否位于当前左闭右开范围内。
     *
     * @param dateTime 待判断的本地日期时间
     * @return 大于等于开始时刻且小于结束时刻时返回 {@code true}
     * @throws DateOperationException 待判断时刻为空时抛出
     */
    public boolean contains(LocalDateTime dateTime) {
        if (dateTime == null) {
            throw DateOperationException.invalidArgument("dateTime");
        }
        return !dateTime.isBefore(startInclusive) && dateTime.isBefore(endExclusive);
    }

    /**
     * 获取两个端点之间的本地时间持续长度。
     *
     * @return 严格大于零的持续时间
     */
    public Duration duration() {
        return Duration.between(startInclusive, endExclusive);
    }
}

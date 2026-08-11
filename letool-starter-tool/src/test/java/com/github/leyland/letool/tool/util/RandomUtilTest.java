package com.github.leyland.letool.tool.util;

import com.github.leyland.letool.tool.random.RandomErrorCode;
import com.github.leyland.letool.tool.random.RandomOperationException;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 安全随机工具关键边界测试。
 */
class RandomUtilTest {

    /**
     * 验证闭区间整数生成支持完整的 {@code int} 数值范围。
     */
    @Test
    void shouldSupportFullIntegerRangeWithoutOverflow() {
        for (int index = 0; index < 16; index++) {
            int value = RandomUtil.nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE);
            assertTrue(value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE);
        }
    }

    /**
     * 验证闭区间长整数生成不会因范围差值溢出而退化为常量。
     */
    @Test
    void shouldSupportFullLongRangeWithoutPrecisionLoss() {
        Set<Long> values = new HashSet<>();
        for (int index = 0; index < 16; index++) {
            values.add(RandomUtil.nextLong(Long.MIN_VALUE, Long.MAX_VALUE));
        }

        assertTrue(values.size() > 1);
    }

    /**
     * 验证反向范围和非有限浮点边界转换为稳定随机参数异常。
     */
    @Test
    void shouldRejectInvalidNumericRanges() {
        RandomOperationException integerRange = assertThrows(
                RandomOperationException.class,
                () -> RandomUtil.nextInt(2, 1)
        );
        RandomOperationException longRange = assertThrows(
                RandomOperationException.class,
                () -> RandomUtil.nextLong(2L, 1L)
        );
        RandomOperationException doubleRange = assertThrows(
                RandomOperationException.class,
                () -> RandomUtil.nextDouble(Double.NaN, 1D)
        );

        assertEquals(RandomErrorCode.INVALID_RANGE.getCode(), integerRange.getCode());
        assertEquals(RandomErrorCode.INVALID_RANGE.getCode(), longRange.getCode());
        assertEquals(RandomErrorCode.INVALID_RANGE.getCode(), doubleRange.getCode());
    }

    /**
     * 验证随机字符串允许零长度，但拒绝负长度和空字符表。
     */
    @Test
    void shouldValidateRandomStringArguments() {
        assertEquals("", RandomUtil.randomString(0));

        RandomOperationException negativeLength = assertThrows(
                RandomOperationException.class,
                () -> RandomUtil.randomNumbers(-1)
        );
        RandomOperationException emptyAlphabet = assertThrows(
                RandomOperationException.class,
                () -> RandomUtil.randomString("", 6)
        );

        assertEquals(RandomErrorCode.INVALID_LENGTH.getCode(), negativeLength.getCode());
        assertEquals(RandomErrorCode.INVALID_ALPHABET.getCode(), emptyAlphabet.getCode());
    }

    /**
     * 验证数字验证码复用安全随机字符生成契约。
     */
    @Test
    void shouldGenerateNumericVerificationCode() {
        String code = RandomUtil.randomCode(6);

        assertEquals(6, code.length());
        assertTrue(code.matches("\\d{6}"));
    }
}

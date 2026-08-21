package io.github.leylaragg.letool.print.xml.format;

import io.github.leylaragg.letool.print.xml.XmlDsl;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 在展开普通十进制文本前统一执行字符容量检查。
 *
 * @author leyland
 */
final class BoundedDecimalText {

    /** 只提供包内静态转换。 */
    private BoundedDecimalText() {
    }

    /**
     * 生成不会超过模板文本治理上限的普通十进制文本。
     *
     * @param number 待转换数字
     * @return 去掉无意义尾零的普通十进制文本
     */
    static String toPlainString(BigDecimal number) {
        return normalize(number).toPlainString();
    }

    /**
     * 去掉尾随零并在文本展开前检查字符数。
     *
     * @param number 待检查数字
     * @return 可以安全交给后续格式化器的数字
     */
    static BigDecimal normalize(BigDecimal number) {
        BigDecimal normalized = Objects.requireNonNull(number, "number 不能为空")
                .stripTrailingZeros();
        long precision = normalized.precision();
        long scale = normalized.scale();
        long length;
        if (scale <= 0) {
            length = precision - scale;
        } else if (precision > scale) {
            length = precision + 1;
        } else {
            length = scale + 2;
        }
        if (normalized.signum() < 0) {
            length++;
        }
        if (length > XmlDsl.MAX_GENERATED_TEXT_CHARACTERS) {
            throw new IllegalArgumentException("数字文本字符数量超过限制");
        }
        return normalized;
    }
}

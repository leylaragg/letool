package io.github.leylaragg.letool.print.document;

import io.github.leylaragg.letool.print.exception.PrintValidationException;

import java.util.OptionalInt;

/**
 * 页面序列参与逻辑页码计算的不可变规则。
 *
 * @author leyland
 */
public final class PageNumbering {

    /** 延续前序逻辑页码的共享规则。 */
    private static final PageNumbering COUNTED = new PageNumbering(true, null);

    /** 不参与逻辑页码的共享规则。 */
    private static final PageNumbering EXCLUDED = new PageNumbering(false, null);

    /** 是否计入逻辑总页数。 */
    private final boolean includedInCount;

    /** 可选重新起算值。 */
    private final Integer restartAt;

    /** 创建页码规则。 */
    private PageNumbering(boolean includedInCount, Integer restartAt) {
        this.includedInCount = includedInCount;
        this.restartAt = restartAt;
    }

    /** @return 计入总数并延续前序页码的规则 */
    public static PageNumbering counted() {
        return COUNTED;
    }

    /**
     * 创建从指定正整数重新编号的规则。
     *
     * @param firstPageNumber 本序列首页逻辑页码
     * @return 重新编号规则
     */
    public static PageNumbering countedFrom(int firstPageNumber) {
        if (firstPageNumber < 1) {
            throw PrintValidationException.invalidDocument("逻辑起始页码必须大于零");
        }
        return new PageNumbering(true, firstPageNumber);
    }

    /** @return 不参与逻辑页码的规则 */
    public static PageNumbering excluded() {
        return EXCLUDED;
    }

    /** @return 是否计入逻辑总页数 */
    public boolean includedInCount() {
        return includedInCount;
    }

    /** @return 可选重新起算值；为空表示延续前序页码 */
    public OptionalInt restartAt() {
        return restartAt == null ? OptionalInt.empty() : OptionalInt.of(restartAt);
    }
}

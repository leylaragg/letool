package com.github.leyland.letool.tool.model;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分页结果模型——封装分页查询的标准返回结构.
 *
 * <h3>字段说明</h3>
 * <ul>
 *   <li>{@code records} — 当前页数据列表（非 null，无数据时为空列表）</li>
 *   <li>{@code total} — 符合查询条件的总记录数</li>
 *   <li>{@code page} — 当前页码（最小为 1）</li>
 *   <li>{@code pageSize} — 每页条数（最小为 1）</li>
 *   <li>{@code totalPages} — 总页数（由 total / pageSize 自动计算）</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 分页查询
 * PageResult<User> page = PageResult.of(users, totalCount, 1, 20);
 *
 * // 空结果
 * PageResult<User> empty = PageResult.empty(1, 20);
 *
 * // 类型转换（如 DO → VO）
 * PageResult<UserVO> vos = page.map(UserVO::from);
 * }</pre>
 *
 * @param <T> 数据类型
 */
public class PageResult<T> {

    private List<T> records;
    private long total;
    private int page;
    private int pageSize;
    private int totalPages;

    /**
     * 创建空的分页结果（用于反序列化框架）.
     */
    public PageResult() {}

    /**
     * 创建分页结果.
     *
     * @param records  当前页数据
     * @param total    总记录数
     * @param page     当前页码
     * @param pageSize 每页条数
     */
    public PageResult(List<T> records, long total, int page, int pageSize) {
        this.records = records == null ? Collections.emptyList() : records;
        this.total = total;
        this.page = Math.max(1, page);
        this.pageSize = Math.max(1, pageSize);
        this.totalPages = (int) Math.ceil((double) this.total / this.pageSize);
    }

    /**
     * 创建分页结果的静态工厂方法.
     *
     * @param records  当前页数据
     * @param total    总记录数
     * @param page     当前页码
     * @param pageSize 每页条数
     * @param <T>      数据类型
     * @return 分页结果
     */
    public static <T> PageResult<T> of(List<T> records, long total, int page, int pageSize) {
        return new PageResult<>(records, total, page, pageSize);
    }

    /**
     * 创建空分页结果.
     *
     * @param page     当前页码
     * @param pageSize 每页条数
     * @param <T>      数据类型
     * @return total=0 的分页结果
     */
    public static <T> PageResult<T> empty(int page, int pageSize) {
        return new PageResult<>(Collections.emptyList(), 0, page, pageSize);
    }

    /**
     * 将分页结果中的数据类型转换为另一种类型（如实体转 VO）.
     *
     * <p>注意：返回新的 PageResult 实例，原实例保持不变.</p>
     *
     * @param mapper 类型转换函数
     * @param <R>    目标类型
     * @return 转换后的分页结果（total、page、pageSize 等分页参数与该实例一致）
     */
    public <R> PageResult<R> map(Function<T, R> mapper) {
        List<R> mapped = records.stream().map(mapper).collect(Collectors.toList());
        return new PageResult<>(mapped, total, page, pageSize);
    }

    // ======================== getter / setter ========================

    public List<T> getRecords() { return records; }
    public void setRecords(List<T> records) { this.records = records; }
    public long getTotal() { return total; }

    /**
     * 设置总记录数，同时自动重算 {@code totalPages}.
     */
    public void setTotal(long total) {
        this.total = total;
        this.totalPages = (int) Math.ceil((double) total / Math.max(1, pageSize));
    }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getPageSize() { return pageSize; }

    /**
     * 设置每页条数，同时自动重算 {@code totalPages}.
     */
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
        this.totalPages = (int) Math.ceil((double) this.total / Math.max(1, pageSize));
    }

    /** 获取总页数（由 total 和 pageSize 自动计算）. */
    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
}

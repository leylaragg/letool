package com.github.leyland.letool.data.core;

import java.util.Collections;
import java.util.List;

/**
 * 分页查询结果模型，封装分页数据及分页元信息。
 *
 * <p>包含当前页数据列表、总记录数、当前页码、每页大小和总页数。
 * 由 {@link LetoolTemplate#selectPage} 方法返回。</p>
 *
 * @param <T> 记录类型
 * @author leyland
 * @since 2.0.0
 */
public class PaginationResult<T> {

    /** 当前页数据列表 */
    private List<T> records;

    /** 总记录数 */
    private long total;

    /** 当前页码（从 1 开始） */
    private int page;

    /** 每页大小 */
    private int pageSize;

    /** 总页数（由 total / pageSize 计算得出） */
    private int totalPages;

    /** 构造空的 PaginationResult，records 初始化为空列表。 */
    public PaginationResult() {
        this.records = Collections.emptyList();
    }

    /**
     * 构造分页结果。
     *
     * @param records  当前页数据列表
     * @param total    总记录数
     * @param page     当前页码（从 1 开始）
     * @param pageSize 每页大小
     */
    public PaginationResult(List<T> records, long total, int page, int pageSize) {
        this.records = records != null ? records : Collections.emptyList();
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
        this.totalPages = pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0;
    }

    public List<T> getRecords() { return records; }
    public void setRecords(List<T> records) { this.records = records; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
}

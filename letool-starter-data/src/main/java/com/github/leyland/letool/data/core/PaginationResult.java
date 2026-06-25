package com.github.leyland.letool.data.core;

import java.util.Collections;
import java.util.List;

public class PaginationResult<T> {

    private List<T> records;
    private long total;
    private int page;
    private int pageSize;
    private int totalPages;

    public PaginationResult() {
        this.records = Collections.emptyList();
    }

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

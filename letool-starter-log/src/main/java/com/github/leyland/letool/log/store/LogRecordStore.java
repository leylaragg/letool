package com.github.leyland.letool.log.store;

import java.util.List;

/**
 * 日志记录存储接口 —— 三层存储（内存/文件/数据库）的统一抽象.
 */
public interface LogRecordStore<T> {

    void save(T record);

    List<T> queryRecent(int limit);

    long count();
}

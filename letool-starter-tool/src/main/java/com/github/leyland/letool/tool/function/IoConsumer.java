package com.github.leyland.letool.tool.function;

import java.io.IOException;

/**
 * 允许抛出 I/O 异常的单参数消费函数。
 *
 * @param <T> 被消费的资源类型
 */
@FunctionalInterface
public interface IoConsumer<T> {

    /**
     * 消费资源。
     *
     * @param value 资源
     * @throws IOException I/O 操作失败时抛出
     */
    void accept(T value) throws IOException;
}

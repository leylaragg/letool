package com.github.leyland.letool.print.service;

import com.github.leyland.letool.print.context.PrintContext;

/**
 * 将宿主业务请求转换为版本明确的只读打印上下文。
 *
 * <p>实现通常作为 Spring 单例使用，不应在字段中保存单次请求状态。</p>
 *
 * @param <R> 可信 Java 请求类型
 * @author leyland
 */
@FunctionalInterface
public interface PrintDataAdapter<R> {

    /**
     * 完成业务查询、权限复核、脱敏和打印字段整理。
     *
     * @param request 已通过定义类型检查的业务请求
     * @return 不包含 Entity、Mapper 或业务 Service 的只读上下文
     */
    PrintContext load(R request);
}

package com.github.leyland.letool.print.api;

/**
 * 同步打印统一门面。
 *
 * <p>实现必须可被并发调用；单次请求状态只能保存在调用栈或请求隔离对象中。</p>
 *
 * @author leyland
 */
public interface PrintEngine {

    /**
     * 使用已锁定模板和只读上下文生成一个内存产物。
     *
     * @param request 同步打印请求
     * @return 不可变打印产物
     * @throws com.github.leyland.letool.exception.core.BaseException 请求、路由或执行失败时抛出
     */
    PrintArtifact render(PrintRequest request);
}

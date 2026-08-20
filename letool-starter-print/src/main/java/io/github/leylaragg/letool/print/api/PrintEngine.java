package io.github.leylaragg.letool.print.api;

import java.io.OutputStream;

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
     * @throws io.github.leylaragg.letool.exception.core.BaseException 请求、路由或执行失败时抛出
     */
    PrintArtifact render(PrintRequest request);

    /**
     * 把产物写入调用方提供的目标，不在框架内保留完整内容。
     *
     * @param request 同步打印请求
     * @param output 调用方拥有并负责关闭的输出流
     * @return 产物格式、长度、摘要和安全元数据
     * @throws io.github.leylaragg.letool.exception.core.BaseException 请求、路由、渲染或写出失败时抛出
     */
    PrintResult renderTo(PrintRequest request, OutputStream output);
}

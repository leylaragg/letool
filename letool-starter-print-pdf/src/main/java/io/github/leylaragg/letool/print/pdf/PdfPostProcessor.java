package io.github.leylaragg.letool.print.pdf;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;

/**
 * 最终 PDF 的宿主后处理入口，输出受当前工作区容量约束。
 *
 * @author leyland
 * @date 2026/10/14
 */
@FunctionalInterface
public interface PdfPostProcessor {
    /**
     * 将已经保存并关闭的源 PDF 处理为最终输出。
     *
     * <p>处理器只能通过 {@code boundedTarget} 写入目标，并在正常结束前关闭该流；SDK 在异常路径也会关闭它。
     * {@code target} 仅用于输出流关闭后的只读校验，不能另行打开写入。每次写入同时校验单文件和工作区活动容量，
     * 返回后 SDK 只校验并复制结果。</p>
     *
     * @param source 已关闭的最终源 PDF；处理器只读访问，文件由 SDK 管理
     * @param target 受限流对应的工作区文件，仅用于校验，文件由 SDK 管理
     * @param boundedTarget SDK 提供的受限输出流，处理器负责正常路径的关闭
     * @param maxTargetBytes 当前允许的最大目标容量，单位为字节
     * @throws IOException 处理、写入或只读校验失败
     */
    void process(Path source, Path target, OutputStream boundedTarget, long maxTargetBytes) throws IOException;
}

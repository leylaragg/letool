package com.github.leyland.letool.print.docx;

/**
 * 控制 DOCX 遇到暂时无法原样表达的节点时如何处理。
 *
 * @author leyland
 */
public enum DocxCompatibilityMode {

    /** 保留文档生成能力，并用明确的替代表达承载内容。 */
    COMPATIBLE,

    /** 只接受能够按当前 DOCX 能力完整表达的文档。 */
    STRICT
}

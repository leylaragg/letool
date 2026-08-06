package com.github.leyland.letool.file.model;

/**
 * 文件已存在时采用的覆盖策略。
 */
public enum OverwritePolicy {

    /** 目标已存在时拒绝写入。 */
    FAIL,

    /** 目标已存在时显式替换。 */
    REPLACE
}

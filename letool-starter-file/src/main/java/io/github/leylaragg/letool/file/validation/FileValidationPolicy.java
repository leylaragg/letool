package io.github.leylaragg.letool.file.validation;

/**
 * 用户可以注册的上传校验扩展接口。
 *
 * <p>实现可以根据文件名、声明信息、有限文件头和探测类型拒绝上传；需要完整内容扫描时，
 * 应结合业务隔离区或自定义存储流程完成。</p>
 */
@FunctionalInterface
public interface FileValidationPolicy {

    /**
     * 校验上传上下文，不接受时抛出业务自定义异常或文件异常。
     *
     * @param context 上传校验上下文
     */
    void validate(FileValidationContext context);
}

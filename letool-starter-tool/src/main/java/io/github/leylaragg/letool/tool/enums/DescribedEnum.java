package io.github.leylaragg.letool.tool.enums;

/**
 * 约定业务枚举提供人类可读描述的轻量契约。
 *
 * <p>描述主要用于下拉选项、日志和管理界面展示，不应作为稳定业务编码参与持久化。</p>
 */
public interface DescribedEnum {

    /**
     * 获取业务枚举的展示描述。
     *
     * @return 人类可读描述
     */
    String getDescription();
}

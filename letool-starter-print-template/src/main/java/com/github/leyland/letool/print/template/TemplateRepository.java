package com.github.leyland.letool.print.template;

import java.util.Optional;

/**
 * 按版本保存和切换模板集合的仓库契约。
 *
 * <p>同一版本不能覆盖，激活操作只指向已发布版本。</p>
 *
 * @author leyland
 */
public interface TemplateRepository {

    /**
     * 查找已发布版本。
     *
     * @param version 集合版本
     * @return 已发布集合，不存在时为空
     */
    Optional<TemplateSet> find(long version);

    /** @return 当前激活集合，尚未激活时为空 */
    Optional<TemplateSet> current();

    /**
     * 发布新版本，不切换当前集合。
     *
     * @param templateSet 待发布集合
     * @return 已发布集合
     */
    TemplateSet publish(TemplateSet templateSet);

    /**
     * 在一次原子状态替换中发布并激活新版本。
     *
     * @param templateSet 待发布集合
     * @return 已发布集合
     */
    TemplateSet publishAndActivate(TemplateSet templateSet);

    /**
     * 切换到已有版本。
     *
     * @param version 集合版本
     * @return 激活后的集合
     */
    TemplateSet activate(long version);
}

package io.github.leylaragg.letool.print.template;

import java.util.Optional;

/**
 * 提供当前和历史模板集合快照的只读来源。
 *
 * <p>运行期打印只依赖查询能力，模板发布和版本切换由可写仓库负责。</p>
 *
 * @author leyland
 */
public interface TemplateSource {

    /**
     * 查找指定版本的模板集合。
     *
     * @param version 集合版本
     * @return 已存在的集合，不存在时为空
     */
    Optional<TemplateSet> find(long version);

    /**
     * 返回当前激活的模板集合。
     *
     * @return 当前集合，尚未激活时为空
     */
    Optional<TemplateSet> current();
}

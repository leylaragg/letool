package com.github.leyland.letool.rule.store;

import com.github.leyland.letool.rule.chain.ChainDefinition;

import java.util.List;

/**
 * 规则链存储接口 —— 定义规则链持久化的标准操作.
 *
 * <h3>设计意图</h3>
 * <p>规则引擎支持多种规则存储源（文件、数据库等）。通过此接口抽象，上层
 * {@link com.github.leyland.letool.rule.chain.ChainManager ChainManager}
 * 无需关心底层存储细节.</p>
 *
 * <h3>实现类</h3>
 * <ul>
 *   <li>{@link FileRuleStore} —— 基于 YAML 文件系统的实现</li>
 *   <li>未来可扩展：{@code DatabaseRuleStore} —— 基于数据库的实现</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * RuleStore store = new FileRuleStore("classpath:rule/chains/");
 *
 * // 保存
 * store.save(chainDefinition);
 *
 * // 加载
 * ChainDefinition chain = store.load("riskChain");
 *
 * // 列表
 * List<ChainDefinition> all = store.listAll();
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 * @see FileRuleStore
 * @see ChainDefinition
 */
public interface RuleStore {

    /**
     * 加载指定名称的规则链定义.
     *
     * @param name 规则链名称
     * @return 规则链定义，不存在时返回 null
     */
    ChainDefinition load(String name);

    /**
     * 保存（创建或更新）规则链定义.
     *
     * @param chain 规则链定义
     */
    void save(ChainDefinition chain);

    /**
     * 删除指定名称的规则链定义.
     *
     * @param name 规则链名称
     */
    void delete(String name);

    /**
     * 列出所有规则链定义.
     *
     * @return 所有规则链定义列表
     */
    List<ChainDefinition> listAll();
}

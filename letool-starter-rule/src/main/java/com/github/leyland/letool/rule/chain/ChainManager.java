package com.github.leyland.letool.rule.chain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 规则链管理器 —— 规则链的注册中心，负责规则链的增删改查和生命周期管理.
 *
 * <h3>设计说明</h3>
 * <p>ChainManager 是规则引擎的中央注册表，所有已加载的规则链定义都存储在此。
 * 使用 {@link ConcurrentHashMap} 保证线程安全，支持高并发环境下的读写操作.</p>
 *
 * <h3>核心功能</h3>
 * <ul>
 *   <li>规则链的注册、注销、查询、列表</li>
 *   <li>从目录批量加载 YAML 规则链文件</li>
 *   <li>热更新支持：无锁替换规则链定义</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * ChainManager manager = new ChainManager();
 *
 * // 注册规则链
 * manager.register(chainDefinition);
 *
 * // 查询
 * ChainDefinition chain = manager.get("riskChain");
 *
 * // 批量加载
 * manager.loadFromDirectory("classpath:rule/chains/");
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 * @see ChainDefinition
 * @see ChainParser
 */
public class ChainManager {

    private static final Logger log = LoggerFactory.getLogger(ChainManager.class);

    /** 规则链注册表（线程安全） */
    private final ConcurrentHashMap<String, ChainDefinition> chains = new ConcurrentHashMap<>();

    /** 规则链解析器 */
    private final ChainParser chainParser;

    // ======================== 构造方法 ========================

    /**
     * 创建规则链管理器.
     *
     * @param chainParser 规则链解析器
     */
    public ChainManager(ChainParser chainParser) {
        this.chainParser = chainParser;
    }

    /**
     * 创建规则链管理器（使用默认解析器）.
     */
    public ChainManager() {
        this.chainParser = new ChainParser();
    }

    // ======================== 注册管理 ========================

    /**
     * 注册一个规则链定义.
     *
     * <p>如果已存在同名的规则链，将被覆盖并记录日志.</p>
     *
     * @param chain 规则链定义
     * @throws IllegalArgumentException 如果 chain 或 chain.getName() 为 null
     */
    public void register(ChainDefinition chain) {
        if (chain == null) {
            throw new IllegalArgumentException("规则链定义不能为 null");
        }
        if (chain.getName() == null || chain.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("规则链名称不能为空");
        }
        ChainDefinition old = chains.put(chain.getName(), chain);
        if (old != null) {
            log.info("规则链已更新: {}", chain.getName());
        } else {
            log.info("规则链已注册: {}", chain.getName());
        }
    }

    /**
     * 注销指定名称的规则链.
     *
     * @param name 规则链名称
     * @return 被移除的规则链定义，如果不存在则返回 null
     */
    public ChainDefinition unregister(String name) {
        ChainDefinition removed = chains.remove(name);
        if (removed != null) {
            log.info("规则链已注销: {}", name);
        }
        return removed;
    }

    /**
     * 获取指定名称的规则链定义.
     *
     * @param name 规则链名称
     * @return 规则链定义，不存在时返回 null
     */
    public ChainDefinition get(String name) {
        return chains.get(name);
    }

    /**
     * 列出所有已注册的规则链定义.
     *
     * @return 不可变的规则链列表副本
     */
    public List<ChainDefinition> listAll() {
        return Collections.unmodifiableList(new ArrayList<>(chains.values()));
    }

    /**
     * 检查指定名称的规则链是否已注册.
     *
     * @param name 规则链名称
     * @return true 表示已注册
     */
    public boolean contains(String name) {
        return chains.containsKey(name);
    }

    /**
     * 获取已注册的规则链数量.
     *
     * @return 规则链数量
     */
    public int size() {
        return chains.size();
    }

    // ======================== 文件加载 ========================

    /**
     * 从指定目录加载所有 YAML 规则链文件.
     *
     * <p>遍历目录下的所有 {@code .yml} 和 {@code .yaml} 文件，
     * 解析并注册其中的规则链定义.</p>
     *
     * @param directoryPath 目录路径（支持 classpath: 前缀）
     * @return 加载的规则链数量
     */
    public int loadFromDirectory(String directoryPath) {
        List<ChainDefinition> parsedChains = chainParser.parseDirectory(directoryPath);
        for (ChainDefinition chain : parsedChains) {
            register(chain);
        }
        return parsedChains.size();
    }

    /**
     * 从指定文件加载规则链定义.
     *
     * @param filePath 文件路径（支持 classpath: 前缀）
     * @return 加载的规则链数量
     */
    public int loadFromFile(String filePath) {
        List<ChainDefinition> parsedChains = chainParser.parseFile(filePath);
        for (ChainDefinition chain : parsedChains) {
            register(chain);
        }
        return parsedChains.size();
    }

    // ======================== 热更新 ========================

    /**
     * 热更新指定规则链 —— 在运行时替换规则链定义而不重启服务.
     *
     * <p>新定义会立即生效，之后的执行将使用新的链定义.</p>
     *
     * @param name    规则链名称
     * @param updated 更新后的规则链定义
     * @return 被替换的旧定义，如果名称对应的链不存在则返回 null
     */
    public ChainDefinition reload(String name, ChainDefinition updated) {
        if (updated == null || name == null) {
            return null;
        }
        updated.setName(name);
        ChainDefinition old = chains.put(name, updated);
        if (old != null) {
            log.info("规则链已热更新: {}", name);
        } else {
            log.info("规则链热更新时发现新链（非替换）: {}", name);
        }
        return old;
    }

    // ======================== 全量管理 ========================

    /**
     * 清空所有已注册的规则链.
     */
    public void clearAll() {
        int count = chains.size();
        chains.clear();
        log.info("已清空所有规则链（共 {} 条）", count);
    }

    /**
     * 获取内部注册表（暴露给需要遍历的内部组件）.
     *
     * @return 规则链 Map（只读视图不可修改，但请谨慎使用反射修改）
     */
    public Map<String, ChainDefinition> getChainMap() {
        return Collections.unmodifiableMap(chains);
    }
}

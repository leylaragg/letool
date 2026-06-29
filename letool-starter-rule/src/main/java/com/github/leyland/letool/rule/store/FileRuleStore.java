package com.github.leyland.letool.rule.store;

import com.github.leyland.letool.rule.chain.ChainDefinition;
import com.github.leyland.letool.rule.chain.ChainParser;
import com.github.leyland.letool.tool.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 文件系统规则存储实现 —— 将规则链定义持久化为 YAML 文件.
 *
 * <h3>存储机制</h3>
 * <p>每个规则链保存在独立的 YAML 文件中，文件名为 {@code {chainName}.yml}。
 * 默认路径为 {@code classpath:rule/chains/}，支持通过构造函数自定义.</p>
 *
 * <h3>路径解析</h3>
 * <ul>
 *   <li>以 {@code classpath:} 开头 —— 从 classpath 加载（只读）</li>
 *   <li>非 classpath —— 从文件系统读写</li>
 * </ul>
 *
 * <h3>注意事项</h3>
 * <p>当存储路径为 classpath 时，{@link #save} 和 {@link #delete} 操作将不可用，
 * 因为 classpath 资源在运行时是只读的.</p>
 *
 * @author leyland
 * @since 2.0.0
 * @see RuleStore
 * @see ChainDefinition
 */
public class FileRuleStore implements RuleStore {

    private static final Logger log = LoggerFactory.getLogger(FileRuleStore.class);

    /** 规则链文件存储目录 */
    private final String basePath;

    /** 规则链解析器 */
    private final ChainParser chainParser;

    /** 是否为 classpath 资源（只读） */
    private final boolean classpathMode;

    // ======================== 构造方法 ========================

    /**
     * 创建文件系统规则存储.
     *
     * @param basePath 存储目录路径（支持 classpath: 前缀）
     */
    public FileRuleStore(String basePath) {
        this.basePath = basePath;
        this.chainParser = new ChainParser();
        this.classpathMode = basePath != null && basePath.startsWith("classpath:");
    }

    /**
     * 创建文件系统规则存储（使用默认路径）.
     */
    public FileRuleStore() {
        this("classpath:rule/chains/");
    }

    // ======================== RuleStore 实现 ========================

    /**
     * 加载指定名称的规则链定义.
     *
     * @param name 规则链名称
     * @return 规则链定义，不存在或加载失败时返回 null
     */
    @Override
    public ChainDefinition load(String name) {
        String filePath = resolveFilePath(name);
        List<ChainDefinition> chains = chainParser.parseFile(filePath);
        return chains.isEmpty() ? null : chains.get(0);
    }

    /**
     * 保存规则链定义为文件.
     *
     * <p>使用 JSON 序列化后写入文件（比 YAML 序列化更可靠）。如果为 classpath 模式则操作失败.</p>
     *
     * @param chain 规则链定义
     * @throws UnsupportedOperationException 当为 classpath 模式时
     * @throws com.github.leyland.letool.rule.exception.RuleException 保存失败时
     */
    @Override
    public void save(ChainDefinition chain) {
        if (classpathMode) {
            throw new UnsupportedOperationException("classpath 模式下不支持保存操作，请配置为文件系统路径");
        }
        if (chain == null || chain.getName() == null) {
            throw new IllegalArgumentException("规则链定义或名称为空");
        }

        try {
            Path filePath = Paths.get(resolveFileSystemPath(), chain.getName() + ".yml");
            // 确保目录存在
            Files.createDirectories(filePath.getParent());

            // 序列化为 JSON 保存（更可靠），保留双格式兼容性
            String jsonContent = JsonUtil.toJsonString(chain);
            Files.writeString(filePath, jsonContent, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            log.info("规则链已保存到文件: {}", filePath);
        } catch (IOException e) {
            log.error("保存规则链文件失败: {}", e.getMessage(), e);
            throw new RuntimeException("保存规则链文件失败: " + chain.getName(), e);
        }
    }

    /**
     * 删除指定名称的规则链文件.
     *
     * <p>如果为 classpath 模式则操作失败.</p>
     *
     * @param name 规则链名称
     * @throws UnsupportedOperationException 当为 classpath 模式时
     */
    @Override
    public void delete(String name) {
        if (classpathMode) {
            throw new UnsupportedOperationException("classpath 模式下不支持删除操作，请配置为文件系统路径");
        }

        try {
            Path filePath = Paths.get(resolveFileSystemPath(), name + ".yml");
            Files.deleteIfExists(filePath);
            log.info("规则链文件已删除: {}", filePath);
        } catch (IOException e) {
            log.error("删除规则链文件失败: {}", e.getMessage(), e);
            throw new RuntimeException("删除规则链文件失败: " + name, e);
        }
    }

    /**
     * 列出所有规则链定义.
     *
     * <p>扫描目录下所有 {@code .yml} 和 {@code .yaml} 文件并解析.</p>
     *
     * @return 所有规则链定义列表
     */
    @Override
    public List<ChainDefinition> listAll() {
        try {
            Path dirPath;
            if (classpathMode) {
                // classpath 模式下使用 loader 解析
                return new ChainParser().parseDirectory(basePath);
            } else {
                dirPath = Paths.get(basePath);
                if (!Files.isDirectory(dirPath)) {
                    return Collections.emptyList();
                }

                return Files.list(dirPath)
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".yml") || p.toString().endsWith(".yaml"))
                        .flatMap(p -> {
                            List<ChainDefinition> chains = chainParser.parseFile(p.toString());
                            return chains.stream();
                        })
                        .collect(Collectors.toList());
            }
        } catch (IOException e) {
            log.error("列出规则链文件失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    // ======================== 辅助方法 ========================

    /**
     * 将规则链名称解析为完整文件路径.
     *
     * @param name 规则链名称
     * @return 完整文件路径
     */
    private String resolveFilePath(String name) {
        if (classpathMode) {
            String resourceDir = basePath.substring("classpath:".length());
            // 确保路径以 / 结尾
            if (!resourceDir.endsWith("/")) {
                resourceDir += "/";
            }
            return "classpath:" + resourceDir + name + ".yml";
        }
        return basePath + "/" + name + ".yml";
    }

    /**
     * 解析文件系统路径（去除 classpath: 前缀）.
     *
     * @return 文件系统绝对路径
     */
    private String resolveFileSystemPath() {
        if (classpathMode) {
            throw new UnsupportedOperationException("classpath 资源无法映射为文件系统路径");
        }
        return Paths.get(basePath).toAbsolutePath().toString();
    }
}

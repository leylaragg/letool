package com.github.leyland.letool.rule.hotreload;

import com.github.leyland.letool.rule.chain.ChainDefinition;
import com.github.leyland.letool.rule.chain.ChainManager;
import com.github.leyland.letool.rule.chain.ChainParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 规则热重载监听器 —— 监听规则链文件的变更并自动重新加载.
 *
 * <h3>工作流程</h3>
 * <ol>
 *   <li>通过 {@link FileWatcher} 监听配置目录下的规则链文件</li>
 *   <li>文件变化时触发 {@link #onFileChanged(String)} 回调</li>
 *   <li>重新解析变更的文件，提取规则链定义</li>
 *   <li>通过 {@link ChainManager#reload(String, ChainDefinition)} 热更新链定义</li>
 * </ol>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * RuleHotReloadListener listener = new RuleHotReloadListener(chainManager, chainParser, fileWatcher);
 * listener.start();  // 启动监听
 *
 * // ... 修改规则链文件后自动生效 ...
 *
 * listener.stop();   // 停止监听
 * }</pre>
 *
 * <p>注意：此功能仅在配置 {@code letool.rule.hot-reload.enabled=true}
 * 且存储路径为文件系统路径时生效.</p>
 *
 * @author leyland
 * @since 2.0.0
 * @see FileWatcher
 * @see ChainManager
 * @see ChainParser
 */
public class RuleHotReloadListener {

    private static final Logger log = LoggerFactory.getLogger(RuleHotReloadListener.class);

    /** 规则链管理器 */
    private final ChainManager chainManager;

    /** 规则链解析器 */
    private final ChainParser chainParser;

    /** 文件变更监听器 */
    private final FileWatcher fileWatcher;

    /** 监听目录 */
    private final String watchDirectory;

    // ======================== 构造方法 ========================

    /**
     * 创建规则热重载监听器.
     *
     * @param chainManager   规则链管理器
     * @param chainParser    规则链解析器
     * @param fileWatcher    文件变更监听器
     * @param watchDirectory 监听的目录路径
     */
    public RuleHotReloadListener(ChainManager chainManager, ChainParser chainParser,
                                  FileWatcher fileWatcher, String watchDirectory) {
        this.chainManager = chainManager;
        this.chainParser = chainParser;
        this.fileWatcher = fileWatcher;
        this.watchDirectory = watchDirectory;
    }

    // ======================== 生命周期管理 ========================

    /**
     * 启动热重载监听.
     *
     * <p>注册文件变更回调并启动文件监听器.</p>
     */
    public void start() {
        fileWatcher.watch(watchDirectory, this::onFileChanged);
        fileWatcher.start();
        log.info("规则热重载监听已启动，监听目录: {}", watchDirectory);
    }

    /**
     * 停止热重载监听.
     */
    public void stop() {
        fileWatcher.stop();
        log.info("规则热重载监听已停止");
    }

    // ======================== 文件变更处理 ========================

    /**
     * 文件变更回调 —— 重新解析变更的文件并热更新规则链.
     *
     * @param filePath 变更的文件路径
     */
    public void onFileChanged(String filePath) {
        log.info("规则链文件已变更，正在重新加载: {}", filePath);

        try {
            List<ChainDefinition> chains = chainParser.parseFile(filePath);

            if (chains.isEmpty()) {
                log.warn("文件 {} 解析后未发现有效的规则链定义", filePath);
                return;
            }

            for (ChainDefinition chain : chains) {
                chainManager.reload(chain.getName(), chain);
                log.info("规则链已热更新: {}", chain.getName());
            }

        } catch (Exception e) {
            log.error("热重载规则链文件失败: {} —— {}", filePath, e.getMessage(), e);
        }
    }
}

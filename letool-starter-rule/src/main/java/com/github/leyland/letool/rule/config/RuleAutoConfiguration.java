package com.github.leyland.letool.rule.config;

import com.github.leyland.letool.rule.annotation.RuleComponent;
import com.github.leyland.letool.rule.chain.ChainManager;
import com.github.leyland.letool.rule.chain.ChainParser;
import com.github.leyland.letool.rule.component.NodeComponent;
import com.github.leyland.letool.rule.engine.GroovyScriptEngine;
import com.github.leyland.letool.rule.engine.RuleEngine;
import com.github.leyland.letool.rule.hotreload.FileWatcher;
import com.github.leyland.letool.rule.hotreload.RuleHotReloadListener;
import com.github.leyland.letool.rule.monitor.RuleMonitor;
import com.github.leyland.letool.rule.store.FileRuleStore;
import com.github.leyland.letool.rule.store.RuleStore;
import com.github.leyland.letool.rule.web.RuleController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * letool-starter-rule 模块的 Spring Boot 自动配置类.
 *
 * <h3>负责装配的 Bean</h3>
 * <ol>
 *   <li>{@link RuleProperties} —— 配置属性（通过 @EnableConfigurationProperties 激活）</li>
 *   <li>{@link ChainParser} —— 规则链解析器（YAML/JSON）</li>
 *   <li>{@link ChainManager} —— 规则链管理器（注册中心 + 文件加载）</li>
 *   <li>{@link GroovyScriptEngine} —— Groovy 脚本引擎（可选，有 Groovy 依赖时激活）</li>
 *   <li>{@link RuleEngine} —— 规则引擎核心（编排节点执行）</li>
 *   <li>{@link RuleStore} —— 规则持久化存储（默认 FileRuleStore）</li>
 *   <li>{@link RuleMonitor} —— 执行监控（可选，根据配置激活）</li>
 *   <li>{@link FileWatcher} + {@link RuleHotReloadListener} —— 热重载（可选，根据配置激活）</li>
 *   <li>{@link RuleController} —— REST 管理 API（仅 Web 环境）</li>
 * </ol>
 *
 * <h3>组件扫描机制</h3>
 * <p>启动时自动扫描标注了 {@link RuleComponent @RuleComponent} 的 Spring Bean，
 * 将其注册到 {@link RuleEngine} 的组件注册表中.</p>
 *
 * <h3>激活条件</h3>
 * <ul>
 *   <li>配置项 {@code letool.rule.enabled} 为 {@code true}（默认为 true）</li>
 *   <li>classpath 上存在本模块的自动配置类</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(RuleProperties.class)
@ConditionalOnProperty(prefix = "letool.rule", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RuleAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RuleAutoConfiguration.class);

    // ======================== 核心 Bean ========================

    /**
     * 注册 {@link ChainParser} Bean —— 规则链解析器.
     *
     * @return 规则链解析器实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ChainParser chainParser() {
        log.info("创建 ChainParser Bean");
        return new ChainParser();
    }

    /**
     * 注册 {@link ChainManager} Bean —— 规则链管理器.
     *
     * <p>创建后自动从配置的文件路径加载规则链定义.</p>
     *
     * @param chainParser 规则链解析器
     * @param properties  规则引擎配置属性
     * @return 规则链管理器实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ChainManager chainManager(ChainParser chainParser, RuleProperties properties) {
        log.info("创建 ChainManager Bean, 数据源: {}", properties.getSource());
        ChainManager manager = new ChainManager(chainParser);

        // 如果是文件源，启动时加载规则链文件
        if ("file".equals(properties.getSource())) {
            String path = properties.getFile().getPath();
            log.info("从文件源加载规则链: {}", path);

            // 如果是目录通配符，去掉通配符后缀
            if (path.contains("*")) {
                String dirPath = path.substring(0, path.indexOf('*'));
                if (dirPath.endsWith("/")) {
                    dirPath = dirPath.substring(0, dirPath.length() - 1);
                }
                manager.loadFromDirectory(dirPath);
            } else {
                manager.loadFromDirectory(path);
            }
        }

        return manager;
    }

    /**
     * 注册 {@link GroovyScriptEngine} Bean —— Groovy 脚本引擎.
     *
     * <p>无论 Groovy 是否可用都会创建 Bean，实际使用时内部会检测 Groovy 可用性.</p>
     *
     * @param properties 规则引擎配置属性
     * @return Groovy 脚本引擎实例
     */
    @Bean
    @ConditionalOnMissingBean
    public GroovyScriptEngine groovyScriptEngine(RuleProperties properties) {
        boolean cacheScripts = properties.getGroovy().isCacheScripts();
        long compileTimeout = properties.getGroovy().getCompileTimeout();
        log.info("创建 GroovyScriptEngine Bean (cache={}, timeout={}s)", cacheScripts, compileTimeout);
        return new GroovyScriptEngine(cacheScripts, compileTimeout);
    }

    /**
     * 注册 {@link RuleEngine} Bean —— 规则引擎核心.
     *
     * <p>创建后自动扫描 classpath 中标注了 {@link RuleComponent @RuleComponent} 的
     * Spring Bean，并将其注册到引擎的组件注册表中.</p>
     *
     * @param chainManager       规则链管理器
     * @param groovyScriptEngine Groovy 脚本引擎
     * @param applicationContext Spring 应用上下文
     * @return 规则引擎实例
     */
    @Bean
    @ConditionalOnMissingBean
    public RuleEngine ruleEngine(ChainManager chainManager, GroovyScriptEngine groovyScriptEngine,
                                  ApplicationContext applicationContext) {
        log.info("创建 RuleEngine Bean");

        // 扫描 @RuleComponent 注解的 Bean
        Map<String, NodeComponent> componentRegistry = new ConcurrentHashMap<>();
        Map<String, Object> ruleComponents = applicationContext.getBeansWithAnnotation(RuleComponent.class);

        for (Map.Entry<String, Object> entry : ruleComponents.entrySet()) {
            Object bean = entry.getValue();
            if (bean instanceof NodeComponent) {
                NodeComponent component = (NodeComponent) bean;
                RuleComponent annotation = bean.getClass().getAnnotation(RuleComponent.class);
                String name = annotation.value();
                componentRegistry.put(name, component);
                log.info("发现规则组件: {} (类: {})", name, bean.getClass().getSimpleName());
            }
        }

        RuleEngine engine = new RuleEngine(chainManager, groovyScriptEngine, componentRegistry);
        return engine;
    }

    // ======================== 存储层 Bean ========================

    /**
     * 注册 {@link RuleStore} Bean —— 规则持久化存储.
     *
     * <p>默认使用 {@link FileRuleStore}，基于文件系统。当引入数据库依赖后
     * 可替换为数据库实现.</p>
     *
     * @param properties 规则引擎配置属性
     * @return 规则存储实例
     */
    @Bean
    @ConditionalOnMissingBean
    public RuleStore ruleStore(RuleProperties properties) {
        String path = properties.getFile().getPath();
        log.info("创建 FileRuleStore Bean, 路径: {}", path);
        return new FileRuleStore(path);
    }

    // ======================== 监控 Bean ========================

    /**
     * 注册 {@link RuleMonitor} Bean —— 规则执行监控.
     *
     * <p>仅在配置 {@code letool.rule.monitoring.enabled=true} 时激活.</p>
     *
     * @return 规则监控实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "letool.rule.monitoring", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RuleMonitor ruleMonitor() {
        log.info("创建 RuleMonitor Bean");
        return new RuleMonitor();
    }

    // ======================== 热重载相关 Bean ========================

    /**
     * 注册 {@link FileWatcher} Bean —— 文件变更监听器.
     *
     * <p>仅在配置 {@code letool.rule.hot-reload.enabled=true} 时激活.</p>
     *
     * @param properties 规则引擎配置属性
     * @return 文件监听器实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "letool.rule.hot-reload", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FileWatcher fileWatcher(RuleProperties properties) {
        long interval = properties.getHotReload().getCheckInterval();
        log.info("创建 FileWatcher Bean (间隔: {}s)", interval);
        return new FileWatcher(interval, TimeUnit.SECONDS);
    }

    /**
     * 注册 {@link RuleHotReloadListener} Bean —— 规则热重载监听器.
     *
     * <p>创建后自动启动，监听文件变化并重新加载规则链.
     * 仅在热重载和文件监听都启用时激活.</p>
     *
     * @param chainManager 规则链管理器
     * @param chainParser  规则链解析器
     * @param fileWatcher  文件监听器
     * @param properties   规则引擎配置属性
     * @return 热重载监听器实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "letool.rule.hot-reload", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RuleHotReloadListener ruleHotReloadListener(ChainManager chainManager, ChainParser chainParser,
                                                        FileWatcher fileWatcher, RuleProperties properties) {
        String path = properties.getFile().getPath();
        // 去除通配符获取目录路径
        if (path.contains("*")) {
            path = path.substring(0, path.indexOf('*'));
            if (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
        }
        log.info("创建 RuleHotReloadListener Bean, 监听目录: {}", path);
        RuleHotReloadListener listener = new RuleHotReloadListener(chainManager, chainParser, fileWatcher, path);
        listener.start();
        return listener;
    }

    // ======================== Web 层 Bean ========================

    /**
     * 注册 {@link RuleController} Bean —— 规则引擎 REST 管理 API.
     *
     * <p>仅在 Web 应用环境下激活（{@link ConditionalOnWebApplication}）.</p>
     *
     * @param chainManager 规则链管理器
     * @param ruleEngine   规则引擎
     * @param ruleMonitor  执行监控（可选注入）
     * @return 规则控制器实例
     */
    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnMissingBean
    public RuleController ruleController(ChainManager chainManager, RuleEngine ruleEngine,
                                          @Autowired(required = false) RuleMonitor ruleMonitor) {
        log.info("创建 RuleController Bean (Web 环境)");
        return new RuleController(chainManager, ruleEngine, ruleMonitor);
    }
}

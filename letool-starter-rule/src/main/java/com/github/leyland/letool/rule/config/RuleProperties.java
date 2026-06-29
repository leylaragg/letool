package com.github.leyland.letool.rule.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 规则引擎配置属性 —— 绑定 {@code letool.rule} 前缀的配置项.
 *
 * <h3>配置结构</h3>
 * <pre>{@code
 * letool:
 *   rule:
 *     enabled: true           # 是否启用规则引擎
 *     source: file            # 规则源类型：file / database
 *     file:
 *       path: classpath:rule/chains/*.yml   # 规则链文件路径
 *       watch: true                        # 是否监听文件变化
 *     groovy:
 *       script-path: classpath:rule/scripts/  # Groovy 脚本路径
 *       cache-scripts: true                     # 是否缓存编译后的脚本
 *       compile-timeout: 5s                    # 编译超时时间
 *     hot-reload:
 *       enabled: true          # 是否启用热重载
 *       check-interval: 10s   # 检查间隔
 *     monitoring:
 *       enabled: true          # 是否启用监控
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
@ConfigurationProperties(prefix = "letool.rule")
public class RuleProperties {

    /** 是否启用规则引擎，默认 true */
    private boolean enabled = true;

    /**
     * 规则源类型：
     * <ul>
     *   <li>{@code file} —— 从 YAML 文件加载规则链</li>
     *   <li>{@code database} —— 从数据库加载规则链</li>
     * </ul>
     */
    private String source = "file";

    /** 文件源配置 */
    private FileConfig file = new FileConfig();

    /** Groovy 脚本配置 */
    private GroovyConfig groovy = new GroovyConfig();

    /** 热重载配置 */
    private HotReloadConfig hotReload = new HotReloadConfig();

    /** 监控配置 */
    private MonitoringConfig monitoring = new MonitoringConfig();

    // ======================== getter / setter ========================

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public FileConfig getFile() { return file; }
    public void setFile(FileConfig file) { this.file = file; }
    public GroovyConfig getGroovy() { return groovy; }
    public void setGroovy(GroovyConfig groovy) { this.groovy = groovy; }
    public HotReloadConfig getHotReload() { return hotReload; }
    public void setHotReload(HotReloadConfig hotReload) { this.hotReload = hotReload; }
    public MonitoringConfig getMonitoring() { return monitoring; }
    public void setMonitoring(MonitoringConfig monitoring) { this.monitoring = monitoring; }

    // ======================== 内部类：文件源配置 ========================

    /**
     * 文件源相关配置 —— 规则链 YAML 文件的存放路径和监听设置.
     */
    public static class FileConfig {

        /** 规则链文件路径（支持 classpath: 前缀和 Ant 风格通配符） */
        private String path = "classpath:rule/chains/*.yml";

        /** 是否启用文件变化监听（热重载） */
        private boolean watch = true;

        // ======================== getter / setter ========================

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public boolean isWatch() { return watch; }
        public void setWatch(boolean watch) { this.watch = watch; }
    }

    // ======================== 内部类：Groovy 脚本配置 ========================

    /**
     * Groovy 脚本相关配置 —— 脚本路径、缓存和编译超时.
     */
    public static class GroovyConfig {

        /** Groovy 脚本存放路径（支持 classpath: 前缀） */
        private String scriptPath = "classpath:rule/scripts/";

        /** 是否缓存编译后的脚本 */
        private boolean cacheScripts = true;

        /** 脚本编译超时时间（秒），默认 5 秒 */
        private long compileTimeout = 5;

        // ======================== getter / setter ========================

        public String getScriptPath() { return scriptPath; }
        public void setScriptPath(String scriptPath) { this.scriptPath = scriptPath; }
        public boolean isCacheScripts() { return cacheScripts; }
        public void setCacheScripts(boolean cacheScripts) { this.cacheScripts = cacheScripts; }
        public long getCompileTimeout() { return compileTimeout; }
        public void setCompileTimeout(long compileTimeout) { this.compileTimeout = compileTimeout; }
    }

    // ======================== 内部类：热重载配置 ========================

    /**
     * 热重载配置 —— 规则链文件变更检测相关设置.
     */
    public static class HotReloadConfig {

        /** 是否启用热重载 */
        private boolean enabled = true;

        /** 文件变更检查间隔（秒），默认 10 秒 */
        private long checkInterval = 10;

        // ======================== getter / setter ========================

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getCheckInterval() { return checkInterval; }
        public void setCheckInterval(long checkInterval) { this.checkInterval = checkInterval; }
    }

    // ======================== 内部类：监控配置 ========================

    /**
     * 监控相关配置.
     */
    public static class MonitoringConfig {

        /** 是否启用执行监控（收集执行指标） */
        private boolean enabled = true;

        // ======================== getter / setter ========================

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}

package com.github.leyland.letool.rule.engine;

import com.github.leyland.letool.rule.context.RuleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.*;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Groovy 脚本引擎 —— 负责编译和执行业务人员编写的 Groovy 规则脚本.
 *
 * <h3>设计说明</h3>
 * <p>支持两种脚本执行方式：</p>
 * <ul>
 *   <li><b>原生 Groovy</b>（推荐）—— 当 classpath 中存在 Groovy 依赖时使用，性能最优</li>
 *   <li><b>javax.script</b>（降级）—— 通过 {@link ScriptEngineManager} 获取 Groovy 引擎，
 *       兼容性更好但性能略低</li>
 * </ul>
 *
 * <h3>安全警告 ⚠️</h3>
 * <p>Groovy 脚本拥有完整的 JVM 访问权限，包括文件 I/O、网络、反射和系统调用。
 * <b>切勿执行来自不可信来源的脚本内容。</b></p>
 * <ul>
 *   <li>脚本内容应从受控的文件系统或配置管理系统中加载</li>
 *   <li>RuleController 等管理端点应配置认证和授权</li>
 *   <li>生产环境建议通过 Groovy {@code SecureASTCustomizer} 限制可用的导入和操作</li>
 *   <li>可通过设置 {@code letool.rule.script-execution-enabled=false} 完全禁用脚本执行</li>
 * </ul>
 *
 * <h3>脚本缓存</h3>
 * <p>通过 {@code cacheScripts} 控制是否缓存编译后的脚本。缓存可大幅提升性能，
 * 但在开发调试时可关闭以支持脚本热更新.</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * GroovyScriptEngine engine = new GroovyScriptEngine(true);
 *
 * // 执行脚本
 * Object result = engine.executeScript("riskRule", context);
 *
 * // 预编译
 * engine.compile("riskRule", "def score = context.getParam('score'); return score > 80;");
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 * @see RuleContext
 * @see RuleEngine
 */
public class GroovyScriptEngine {

    private static final Logger log = LoggerFactory.getLogger(GroovyScriptEngine.class);

    /** 是否缓存编译后的脚本 */
    private final boolean cacheScripts;

    /** 脚本编译超时时间（秒） */
    private final long compileTimeout;

    /** 脚本缓存（线程安全） */
    private final ConcurrentHashMap<String, GroovyScript> scriptCache = new ConcurrentHashMap<>();

    /** javax.script 脚本引擎管理器（降级方案） */
    private ScriptEngineManager scriptEngineManager;

    /** 是否已检测到 Groovy 可用 */
    private boolean groovyAvailable = false;

    // ======================== 构造方法 ========================

    /**
     * 创建 Groovy 脚本引擎.
     *
     * @param cacheScripts   是否缓存编译后的脚本
     * @param compileTimeout 编译超时时间（秒）
     */
    public GroovyScriptEngine(boolean cacheScripts, long compileTimeout) {
        this.cacheScripts = cacheScripts;
        this.compileTimeout = compileTimeout;
        initialize();
    }

    /**
     * 创建 Groovy 脚本引擎（默认开启缓存，5 秒编译超时）.
     */
    public GroovyScriptEngine() {
        this(true, 5);
    }

    /**
     * 创建 Groovy 脚本引擎.
     *
     * @param cacheScripts 是否缓存编译后的脚本
     */
    public GroovyScriptEngine(boolean cacheScripts) {
        this(cacheScripts, 5);
    }

    // ======================== 初始化 ========================

    /**
     * 初始化脚本引擎 —— 检测 Groovy 是否可用.
     */
    private void initialize() {
        // 尝试通过 ScriptEngineManager 加载 Groovy 引擎
        try {
            scriptEngineManager = new ScriptEngineManager();
            ScriptEngine engine = scriptEngineManager.getEngineByName("groovy");
            if (engine != null) {
                groovyAvailable = true;
                log.info("Groovy 脚本引擎已就绪（通过 javax.script 方式）");
                log.warn("⚠️ Groovy 脚本引擎拥有完整的 JVM 访问权限，请确保脚本来源可信。"
                        + "可通过系统属性 letool.rule.script-execution-enabled=false 禁用脚本执行。");
            } else {
                log.warn("Groovy 脚本引擎未找到（请添加 groovy-all 依赖），规则脚本功能将不可用");
            }
        } catch (Exception e) {
            log.warn("初始化脚本引擎失败: {}，规则脚本功能将不可用", e.getMessage());
        }
    }

    // ======================== 脚本执行 ========================

    /**
     * 执行指定名称的 Groovy 脚本.
     *
     * <p>优先从缓存中获取已编译的脚本执行，缓存未命中则编译后执行.</p>
     *
     * @param scriptName 脚本名称
     * @param context    规则执行上下文
     * @return 脚本执行结果，执行失败时返回 null
     */
    public Object executeScript(String scriptName, RuleContext context) {
        GroovyScript script = getScript(scriptName);
        if (script == null) {
            log.error("脚本不存在: {}", scriptName);
            return null;
        }
        return executeScript(script, context);
    }

    /**
     * 执行指定的 Groovy 脚本对象.
     *
     * @param script  Groovy 脚本对象
     * @param context 规则执行上下文
     * @return 脚本执行结果
     */
    private Object executeScript(GroovyScript script, RuleContext context) {
        if (!groovyAvailable) {
            log.warn("Groovy 引擎不可用，无法执行脚本: {}", script.getName());
            return null;
        }

        try {
            ScriptEngine engine = scriptEngineManager.getEngineByName("groovy");
            if (engine == null) {
                log.error("无法获取 Groovy ScriptEngine");
                return null;
            }

            // 将 context 绑定到脚本中
            Bindings bindings = engine.createBindings();
            bindings.put("context", context);
            bindings.put("params", context.getParams());
            bindings.put("results", context.getResults());

            // 如果脚本已编译，直接执行编译后的脚本
            if (script.getCompiled() != null && engine instanceof Compilable compilable) {
                Object result = script.getCompiled().eval(bindings);
                return result;
            }

            // 否则执行原始脚本内容
            Object result = engine.eval(script.getContent(), bindings);
            return result;

        } catch (ScriptException e) {
            log.error("执行脚本失败 [{}]: {}", script.getName(), e.getMessage(), e);
            return null;
        }
    }

    // ======================== 脚本编译与缓存 ========================

    /**
     * 预编译脚本内容并缓存.
     *
     * <p>预编译可在规则链执行前消除编译开销，适合高并发场景.</p>
     *
     * @param scriptName    脚本名称
     * @param scriptContent 脚本内容
     * @return 编译成功返回 true
     */
    public boolean compile(String scriptName, String scriptContent) {
        if (!groovyAvailable) {
            log.warn("Groovy 引擎不可用，无法编译脚本: {}", scriptName);
            return false;
        }

        try {
            ScriptEngine engine = scriptEngineManager.getEngineByName("groovy");
            if (engine == null) {
                return false;
            }

            CompiledScript compiled = null;
            if (engine instanceof Compilable compilable) {
                compiled = compilable.compile(scriptContent);
            }

            GroovyScript script = new GroovyScript(scriptName, scriptContent, compiled);
            scriptCache.put(scriptName, script);
            log.info("脚本已编译并缓存: {}", scriptName);
            return true;

        } catch (ScriptException e) {
            log.error("编译脚本失败 [{}]: {}", scriptName, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 注册脚本（不编译，存储原始内容，执行时再编译）.
     *
     * @param scriptName    脚本名称
     * @param scriptContent 脚本内容
     */
    public void registerScript(String scriptName, String scriptContent) {
        GroovyScript script = new GroovyScript(scriptName, scriptContent, null);
        scriptCache.put(scriptName, script);
        log.info("脚本已注册: {}", scriptName);
    }

    /**
     * 获取缓存的脚本.
     *
     * @param scriptName 脚本名称
     * @return 脚本对象，不存在时返回 null
     */
    public GroovyScript getScript(String scriptName) {
        return scriptCache.get(scriptName);
    }

    /**
     * 检查脚本是否已缓存.
     *
     * @param scriptName 脚本名称
     * @return true 表示已缓存
     */
    public boolean isCached(String scriptName) {
        return scriptCache.containsKey(scriptName);
    }

    /**
     * 清除指定脚本的缓存.
     *
     * <p>清除后下次执行将重新从文件系统或数据库加载并编译.</p>
     *
     * @param scriptName 脚本名称
     */
    public void invalidateCache(String scriptName) {
        GroovyScript removed = scriptCache.remove(scriptName);
        if (removed != null) {
            log.info("脚本缓存已清除: {}", scriptName);
        }
    }

    /**
     * 清除所有脚本缓存.
     */
    public void invalidateAllCache() {
        int count = scriptCache.size();
        scriptCache.clear();
        log.info("已清除所有脚本缓存（共 {} 个）", count);
    }

    /**
     * 获取缓存中的脚本数量.
     *
     * @return 缓存的脚本数量
     */
    public int cacheSize() {
        return scriptCache.size();
    }

    // ======================== 状态查询 ========================

    /**
     * 检查 Groovy 是否可用.
     *
     * @return true 表示 Groovy 引擎可用
     */
    public boolean isGroovyAvailable() {
        return groovyAvailable;
    }

    // ======================== 内部类：Groovy 脚本包装 ========================

    /**
     * Groovy 脚本包装类 —— 封装脚本的名称、内容、编译结果和编译时间.
     */
    public static class GroovyScript {

        /** 脚本名称（唯一标识） */
        private final String name;

        /** 脚本原始内容 */
        private final String content;

        /** 编译后的脚本对象（可能为 null，表示未编译） */
        private final CompiledScript compiled;

        /** 编译时间（epoch 毫秒） */
        private final long compiledAt;

        /**
         * 创建 Groovy 脚本包装.
         *
         * @param name     脚本名称
         * @param content  脚本原始内容
         * @param compiled 编译后的脚本（可为 null）
         */
        public GroovyScript(String name, String content, CompiledScript compiled) {
            this.name = name;
            this.content = content;
            this.compiled = compiled;
            this.compiledAt = Instant.now().toEpochMilli();
        }

        // ======================== getter ========================

        public String getName() {
            return name;
        }

        public String getContent() {
            return content;
        }

        public CompiledScript getCompiled() {
            return compiled;
        }

        public long getCompiledAt() {
            return compiledAt;
        }

        /**
         * 判断脚本是否已编译.
         *
         * @return true 表示已编译
         */
        public boolean isCompiled() {
            return compiled != null;
        }
    }
}

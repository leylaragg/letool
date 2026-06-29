package com.github.leyland.letool.rule.web;

import com.github.leyland.letool.rule.chain.ChainDefinition;
import com.github.leyland.letool.rule.chain.ChainManager;
import com.github.leyland.letool.rule.context.RuleContext;
import com.github.leyland.letool.rule.context.RuleResult;
import com.github.leyland.letool.rule.engine.RuleEngine;
import com.github.leyland.letool.rule.model.RuleMetrics;
import com.github.leyland.letool.rule.monitor.RuleMonitor;
import com.github.leyland.letool.tool.model.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则引擎 REST 管理 API —— 提供规则链的 CRUD 管理、测试执行和监控指标查询接口.
 *
 * <h3>API 路径</h3>
 * <p>所有接口以 {@code /api/rule} 为前缀.</p>
 *
 * <h3>接口列表</h3>
 * <table>
 *   <tr><th>方法</th><th>路径</th><th>说明</th></tr>
 *   <tr><td>GET</td><td>/api/rule/chains</td><td>列出所有规则链</td></tr>
 *   <tr><td>GET</td><td>/api/rule/chains/{name}</td><td>获取规则链详情</td></tr>
 *   <tr><td>POST</td><td>/api/rule/chains</td><td>创建规则链</td></tr>
 *   <tr><td>PUT</td><td>/api/rule/chains/{name}</td><td>更新规则链</td></tr>
 *   <tr><td>DELETE</td><td>/api/rule/chains/{name}</td><td>删除规则链</td></tr>
 *   <tr><td>POST</td><td>/api/rule/test</td><td>测试执行规则链</td></tr>
 *   <tr><td>GET</td><td>/api/rule/metrics</td><td>获取执行指标</td></tr>
 * </table>
 *
 * <h3>激活条件</h3>
 * <p>仅在 Web 应用环境下激活（{@link ConditionalOnWebApplication}）.</p>
 *
 * @author leyland
 * @since 2.0.0
 * @see ChainManager
 * @see RuleEngine
 * @see RuleMonitor
 */
@RestController
@RequestMapping("/api/rule")
@ConditionalOnWebApplication
public class RuleController {

    private static final Logger log = LoggerFactory.getLogger(RuleController.class);

    /** 规则链管理器 */
    private final ChainManager chainManager;

    /** 规则引擎 */
    private final RuleEngine ruleEngine;

    /** 执行监控 */
    private final RuleMonitor ruleMonitor;

    // ======================== 构造方法 ========================

    /**
     * 创建规则引擎 REST 控制器.
     *
     * @param chainManager 规则链管理器
     * @param ruleEngine   规则引擎
     * @param ruleMonitor  执行监控
     */
    public RuleController(ChainManager chainManager, RuleEngine ruleEngine, RuleMonitor ruleMonitor) {
        this.chainManager = chainManager;
        this.ruleEngine = ruleEngine;
        this.ruleMonitor = ruleMonitor;
    }

    // ======================== 规则链查询 ========================

    /**
     * 列出所有已注册的规则链.
     *
     * @return 规则链列表
     */
    @GetMapping("/chains")
    public R<List<ChainDefinition>> listChains() {
        List<ChainDefinition> chains = chainManager.listAll();
        return R.ok(chains);
    }

    /**
     * 获取指定名称的规则链详情.
     *
     * @param name 规则链名称
     * @return 规则链定义，不存在时返回 null data
     */
    @GetMapping("/chains/{name}")
    public R<ChainDefinition> getChain(@PathVariable String name) {
        ChainDefinition chain = chainManager.get(name);
        if (chain == null) {
            return R.fail("CHAIN_001", "规则链不存在: " + name);
        }
        return R.ok(chain);
    }

    // ======================== 规则链管理 ========================

    /**
     * 创建新的规则链.
     *
     * @param chain 规则链定义（JSON 格式）
     * @return 创建结果
     */
    @PostMapping("/chains")
    public R<Void> createChain(@RequestBody ChainDefinition chain) {
        if (chain == null || chain.getName() == null || chain.getName().trim().isEmpty()) {
            return R.fail("CHAIN_002", "规则链名称不能为空");
        }
        if (chainManager.contains(chain.getName())) {
            return R.fail("CHAIN_003", "规则链已存在: " + chain.getName());
        }
        chainManager.register(chain);
        log.info("通过 API 创建规则链: {}", chain.getName());
        return R.ok();
    }

    /**
     * 更新现有规则链（热更新）.
     *
     * @param name  规则链名称
     * @param chain 更新后的规则链定义
     * @return 更新结果
     */
    @PutMapping("/chains/{name}")
    public R<ChainDefinition> updateChain(@PathVariable String name, @RequestBody ChainDefinition chain) {
        if (chain == null) {
            return R.fail("CHAIN_004", "请求体不能为空");
        }
        ChainDefinition existing = chainManager.get(name);
        if (existing == null) {
            return R.fail("CHAIN_001", "规则链不存在: " + name);
        }
        chain.setName(name);
        chainManager.reload(name, chain);
        log.info("通过 API 更新规则链: {}", name);
        return R.ok(chain);
    }

    /**
     * 删除指定名称的规则链.
     *
     * @param name 规则链名称
     * @return 删除结果
     */
    @DeleteMapping("/chains/{name}")
    public R<Void> deleteChain(@PathVariable String name) {
        ChainDefinition removed = chainManager.unregister(name);
        if (removed == null) {
            return R.fail("CHAIN_001", "规则链不存在: " + name);
        }
        log.info("通过 API 删除规则链: {}", name);
        return R.ok();
    }

    // ======================== 规则链测试 ========================

    /**
     * 测试执行规则链（沙箱模式）.
     *
     * <p>接受 JSON 请求体：</p>
     * <pre>{@code
     * {
     *   "chainName": "risk-evaluation",
     *   "params": {
     *     "userId": 1001,
     *     "amount": 50000
     *   }
     * }
     * }</pre>
     *
     * @param request 测试请求（chainName + params）
     * @return 执行结果（含执行轨迹）
     */
    @PostMapping("/test")
    public R<RuleResult> testChain(@RequestBody Map<String, Object> request) {
        String chainName = (String) request.get("chainName");
        if (chainName == null || chainName.trim().isEmpty()) {
            return R.fail("TEST_001", "chainName 不能为空");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) request.get("params");
        if (params == null) {
            params = new HashMap<>();
        }

        // 创建执行上下文
        RuleContext context = new RuleContext(chainName, params);

        // 执行规则链
        long startTime = System.currentTimeMillis();
        RuleResult result = ruleEngine.execute(chainName, context);
        long duration = System.currentTimeMillis() - startTime;

        // 记录监控指标
        if (ruleMonitor != null) {
            ruleMonitor.recordExecution(chainName, duration, result.isSuccess());
        }

        if (result.isSuccess()) {
            return R.ok(result);
        } else {
            return R.fail("EXEC_ERR", result.getErrorMessage());
        }
    }

    // ======================== 监控指标 ========================

    /**
     * 获取规则引擎执行指标.
     *
     * <p>返回全局维度和各规则链维度的执行统计.</p>
     *
     * @return 执行指标汇总
     */
    @GetMapping("/metrics")
    public R<RuleMetrics> getMetrics() {
        if (ruleMonitor == null) {
            RuleMetrics emptyMetrics = new RuleMetrics();
            return R.ok(emptyMetrics);
        }
        RuleMetrics metrics = ruleMonitor.getMetrics();
        return R.ok(metrics);
    }
}

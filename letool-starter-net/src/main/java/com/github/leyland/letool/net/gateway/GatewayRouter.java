package com.github.leyland.letool.net.gateway;

import com.github.leyland.letool.net.exception.NetException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网关路由器 —— 管理所有网关路由的注册和查找，是 {@link NetGateway} 的路由选择核心.
 *
 * <p>路由以 {@code routeId} 作为唯一键存储在 {@link ConcurrentHashMap} 中，支持高并发读写.</p>
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>路由注册和注销</li>
 *   <li>路由查找（按 routeId）</li>
 *   <li>路由列表查询</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 */
public class GatewayRouter {

    private static final Logger log = LoggerFactory.getLogger(GatewayRouter.class);

    // ======================== 字段 ========================

    /** 路由表（routeId -> GatewayRoute），线程安全 */
    private final Map<String, GatewayRoute> routes = new ConcurrentHashMap<>();

    // ======================== 路由管理 ========================

    /**
     * 注册一条网关路由.
     *
     * @param route 路由定义
     * @throws NetException 如果 routeId 已存在
     */
    public void registerRoute(GatewayRoute route) {
        if (route == null || route.getRouteId() == null) {
            throw new NetException("Route and routeId must not be null");
        }
        if (routes.containsKey(route.getRouteId())) {
            throw new NetException("Route already registered: " + route.getRouteId());
        }
        routes.put(route.getRouteId(), route);
        log.info("Route registered: {} (type={}, servers={})",
                route.getRouteId(), route.getType(), route.getServers().size());
    }

    /**
     * 注销一条路由.
     *
     * @param routeId 路由标识
     * @return {@code true} 如果找到并移除
     */
    public boolean removeRoute(String routeId) {
        GatewayRoute removed = routes.remove(routeId);
        if (removed != null) {
            log.info("Route removed: {}", routeId);
            return true;
        }
        return false;
    }

    /**
     * 按 routeId 查找路由.
     *
     * @param routeId 路由标识
     * @return 路由定义，未找到返回 {@code null}
     */
    public GatewayRoute route(String routeId) {
        return routes.get(routeId);
    }

    /**
     * 获取所有已注册路由的列表.
     *
     * @return 路由列表（副本）
     */
    public List<GatewayRoute> allRoutes() {
        return new ArrayList<>(routes.values());
    }

    /**
     * 检查路由是否已注册.
     *
     * @param routeId 路由标识
     * @return {@code true} 如果已存在
     */
    public boolean containsRoute(String routeId) {
        return routes.containsKey(routeId);
    }

    /**
     * 获取已注册路由数量.
     *
     * @return 路由数量
     */
    public int getRouteCount() {
        return routes.size();
    }
}

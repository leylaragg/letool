package com.github.leyland.letool.tool.util;

import org.springframework.beans.BeanUtils;
import org.springframework.cglib.beans.BeanCopier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bean 操作工具类——属性拷贝、Map 与 Bean 互转、批量转换.
 *
 * <h3>两种拷贝策略</h3>
 * <table>
 *   <tr><th>方法</th><th>底层实现</th><th>性能</th><th>适用场景</th></tr>
 *   <tr><td>{@link #copy(Object, Class)}</td><td>Spring BeanUtils（反射）</td><td>中</td><td>单次操作</td></tr>
 *   <tr><td>{@link #copyFast(Object, Class)}</td><td>CGLIB BeanCopier（字节码）</td><td>高</td><td>批量操作、高频调用</td></tr>
 * </table>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 单次拷贝
 * UserVO vo = BeanUtil.copy(user, UserVO.class);
 *
 * // 批量拷贝（使用 BeanCopier 缓存，性能更高）
 * List<UserVO> vos = BeanUtil.copyListFast(users, UserVO.class);
 *
 * // Map → Bean
 * User user = BeanUtil.toBean(map, User.class);
 * }</pre>
 */
public final class BeanUtil {

    /**
     * BeanCopier 缓存——key 为 "sourceClassName->targetClassName".
     *
     * <p>BeanCopier 创建成本高（字节码生成），但拷贝性能极高（避免反射），适合批量场景缓存复用.</p>
     */
    private static final Map<String, BeanCopier> COPIER_CACHE = new ConcurrentHashMap<>();

    private BeanUtil() {}

    // ======================== 属性拷贝 ========================

    /**
     * 拷贝对象属性到新实例（基于 Spring BeanUtils，反射）.
     *
     * <p>先通过无参构造器创建目标对象，再逐一拷贝同名属性.</p>
     *
     * @param source      源对象
     * @param targetClass 目标类型（必须有 public 无参构造器）
     * @param <S>         源类型
     * @param <T>         目标类型
     * @return 目标对象，{@code source} 为 {@code null} 返回 {@code null}
     */
    public static <S, T> T copy(S source, Class<T> targetClass) {
        if (source == null) return null;
        T target = newInstance(targetClass);
        copyProperties(source, target);
        return target;
    }

    /**
     * 拷贝源对象属性到目标对象（原地修改目标对象，基于 Spring BeanUtils）.
     *
     * @param source 源对象
     * @param target 目标对象
     * @param <S>    源类型
     * @param <T>    目标类型
     */
    public static <S, T> void copyProperties(S source, T target) {
        if (source == null || target == null) return;
        BeanUtils.copyProperties(source, target);
    }

    // ======================== 高性能拷贝（CGLIB BeanCopier） ========================

    /**
     * 高性能拷贝——使用 CGLIB BeanCopier（字节码生成，避免反射）.
     *
     * <p>首次调用某对类型组合时会生成字节码并缓存，后续调用直接复用，性能远高于反射.
     * 适用于批量拷贝场景（如一次拷贝数千条记录）.</p>
     *
     * @param source      源对象
     * @param targetClass 目标类型
     * @param <S>         源类型
     * @param <T>         目标类型
     * @return 目标对象，{@code source} 为 {@code null} 返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    public static <S, T> T copyFast(S source, Class<T> targetClass) {
        if (source == null) return null;
        T target = newInstance(targetClass);
        String key = source.getClass().getName() + "->" + targetClass.getName();
        BeanCopier copier = COPIER_CACHE.computeIfAbsent(key,
                k -> BeanCopier.create(source.getClass(), targetClass, false));
        copier.copy(source, target, null);
        return target;
    }

    // ======================== 批量拷贝 ========================

    /**
     * 批量拷贝列表（反射方式，适合少量数据）.
     *
     * @param sourceList  源列表
     * @param targetClass 目标类型
     * @param <S>         源类型
     * @param <T>         目标类型
     * @return 新列表，{@code sourceList} 为空返回空列表
     */
    public static <S, T> List<T> copyList(List<S> sourceList, Class<T> targetClass) {
        if (CollUtil.isEmpty(sourceList)) return Collections.emptyList();
        List<T> targets = new ArrayList<>(sourceList.size());
        for (S source : sourceList) {
            targets.add(copy(source, targetClass));
        }
        return targets;
    }

    /**
     * 批量拷贝列表（CGLIB 高性能方式，适合大量数据）.
     *
     * @param sourceList  源列表
     * @param targetClass 目标类型
     * @param <S>         源类型
     * @param <T>         目标类型
     * @return 新列表，{@code sourceList} 为空返回空列表
     */
    public static <S, T> List<T> copyListFast(List<S> sourceList, Class<T> targetClass) {
        if (CollUtil.isEmpty(sourceList)) return Collections.emptyList();
        List<T> targets = new ArrayList<>(sourceList.size());
        for (S source : sourceList) {
            targets.add(copyFast(source, targetClass));
        }
        return targets;
    }

    // ======================== Map 互转 ========================

    /**
     * JavaBean → Map（委托给 {@link JsonUtil#toMap(Object)}，基于 Fastjson2 序列化）.
     *
     * @param obj 任意 JavaBean
     * @return 字段名 → 值的 LinkedHashMap，{@code obj} 为 {@code null} 返回 {@code null}
     */
    public static Map<String, Object> toMap(Object obj) {
        if (obj == null) return null;
        return JsonUtil.toMap(obj);
    }

    /**
     * Map → JavaBean（委托给 {@link JsonUtil#toBean(Map, Class)}）.
     *
     * @param map   key 为字段名，value 为字段值的 Map
     * @param clazz 目标类型
     * @param <T>   目标泛型
     * @return Bean 实例，{@code map} 为 {@code null} 返回 {@code null}
     */
    public static <T> T toBean(Map<String, Object> map, Class<T> clazz) {
        if (map == null) return null;
        return JsonUtil.toBean(map, clazz);
    }

    // ======================== 实例化 ========================

    /**
     * 通过无参构造器创建实例.
     *
     * @param clazz 目标类型
     * @param <T>   目标泛型
     * @return 新实例
     * @throws RuntimeException 如果没有 public 无参构造器或构造失败
     */
    public static <T> T newInstance(Class<T> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate " + clazz.getName(), e);
        }
    }
}

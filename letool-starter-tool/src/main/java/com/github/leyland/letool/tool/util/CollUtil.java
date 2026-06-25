package com.github.leyland.letool.tool.util;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 集合工具类——判空、创建、交集/并集/差集、分片、提取等操作.
 *
 * <p>所有方法均为空安全：传入 {@code null} 返回安全的默认值而非抛出 NPE.</p>
 */
public final class CollUtil {

    private CollUtil() {}

    // ======================== 判空 ========================

    /**
     * 集合是否为 {@code null} 或无元素.
     */
    public static boolean isEmpty(Collection<?> coll) {
        return coll == null || coll.isEmpty();
    }

    /**
     * 集合是否至少含有一个元素.
     */
    public static boolean isNotEmpty(Collection<?> coll) {
        return !isEmpty(coll);
    }

    /**
     * Map 是否为 {@code null} 或无条目.
     */
    public static boolean isEmpty(Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    /**
     * Map 是否至少含有一个条目.
     */
    public static boolean isNotEmpty(Map<?, ?> map) {
        return !isEmpty(map);
    }

    // ======================== 创建 ========================

    /**
     * 快速创建可变 ArrayList.
     *
     * @param elements 元素，可空
     * @return 包含指定元素的可变列表
     */
    @SafeVarargs
    public static <T> List<T> newArrayList(T... elements) {
        if (elements == null || elements.length == 0) return new ArrayList<>();
        List<T> list = new ArrayList<>(elements.length);
        Collections.addAll(list, elements);
        return list;
    }

    /**
     * 快速创建 HashSet.
     *
     * @param elements 元素，可空
     * @return 包含指定元素的 HashSet
     */
    @SafeVarargs
    public static <T> Set<T> newHashSet(T... elements) {
        if (elements == null || elements.length == 0) return new HashSet<>();
        Set<T> set = new HashSet<>(elements.length);
        Collections.addAll(set, elements);
        return set;
    }

    // ======================== 默认值 ========================

    /**
     * 列表为空时返回默认列表.
     *
     * @param list        源列表
     * @param defaultList 默认列表
     * @return {@code list} 非空则返回自身，否则返回 {@code defaultList}
     */
    public static <T> List<T> defaultIfEmpty(List<T> list, List<T> defaultList) {
        return isEmpty(list) ? defaultList : list;
    }

    // ======================== 提取 ========================

    /**
     * 从列表中提取某个字段构成新列表.
     *
     * <pre>{@code
     * List<Integer> lengths = CollUtil.extract(list, String::length);
     * }</pre>
     *
     * @param list   源列表
     * @param mapper 字段提取函数
     * @param <R>    提取结果类型
     * @return 提取后的不可变列表
     */
    public static <T, R> List<R> extract(List<T> list, Function<T, R> mapper) {
        if (isEmpty(list)) return Collections.emptyList();
        return list.stream().map(mapper).collect(Collectors.toList());
    }

    /**
     * 将列表转为 Map，以指定字段为 key.
     *
     * <p>key 冲突时保留第一个元素.</p>
     *
     * @param list      源列表
     * @param keyMapper key 提取函数
     * @param <K>       key 类型
     * @return 转换后的 Map
     */
    public static <T, K> Map<K, T> toMap(List<T> list, Function<T, K> keyMapper) {
        if (isEmpty(list)) return Collections.emptyMap();
        return list.stream().collect(Collectors.toMap(keyMapper, Function.identity(), (a, b) -> a));
    }

    // ======================== 集合运算 ========================

    /**
     * 取两个集合的交集.
     *
     * @param a 集合 A
     * @param b 集合 B
     * @return A 与 B 的交集，任一为空则返回空列表
     */
    public static <T> List<T> intersection(Collection<T> a, Collection<T> b) {
        if (isEmpty(a) || isEmpty(b)) return Collections.emptyList();
        Set<T> set = new HashSet<>(a);
        set.retainAll(b);
        return new ArrayList<>(set);
    }

    /**
     * 取两个集合的并集（去重，保持插入顺序）.
     *
     * @param a 集合 A
     * @param b 集合 B
     * @return A 与 B 的并集
     */
    public static <T> List<T> union(Collection<T> a, Collection<T> b) {
        Set<T> set = new LinkedHashSet<>();
        if (isNotEmpty(a)) set.addAll(a);
        if (isNotEmpty(b)) set.addAll(b);
        return new ArrayList<>(set);
    }

    /**
     * 取 A 对 B 的差集（A - B）.
     *
     * @param a 被减集合
     * @param b 减集合
     * @return 存在于 A 但不存在于 B 的元素
     */
    public static <T> List<T> subtract(Collection<T> a, Collection<T> b) {
        if (isEmpty(a)) return Collections.emptyList();
        if (isEmpty(b)) return new ArrayList<>(a);
        Set<T> set = new LinkedHashSet<>(a);
        set.removeAll(b);
        return new ArrayList<>(set);
    }

    // ======================== 分片 ========================

    /**
     * 将列表按指定大小切分为多个子列表.
     *
     * <p>最后一个子列表可能不足 {@code size} 个元素.</p>
     *
     * <pre>{@code
     * CollUtil.partition([1,2,3,4,5], 2) → [[1,2], [3,4], [5]]
     * }</pre>
     *
     * @param list 源列表
     * @param size 每个子列表的元素数量
     * @return 切分后的二维列表
     */
    public static <T> List<List<T>> partition(List<T> list, int size) {
        if (isEmpty(list)) return Collections.emptyList();
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(new ArrayList<>(list.subList(i, Math.min(i + size, list.size()))));
        }
        return result;
    }
}

package com.github.leyland.letool.tool.util;

import com.github.leyland.letool.tool.value.ValueOperationException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 提供判空、创建、转换、集合运算和分片等常用集合操作。
 *
 * <p>转换和集合运算返回独立的可变快照，调用方可以安全继续组装业务数据。
 * 结果顺序遵循输入集合的首次出现顺序。</p>
 */
public final class CollUtil {

    /**
     * 禁止创建工具类实例。
     */
    private CollUtil() {
    }

    /**
     * 判断集合是否为 {@code null} 或没有元素。
     *
     * @param coll 待检查集合
     * @return 集合为空时返回 {@code true}
     */
    public static boolean isEmpty(Collection<?> coll) {
        return coll == null || coll.isEmpty();
    }

    /**
     * 判断集合是否至少包含一个元素。
     *
     * @param coll 待检查集合
     * @return 集合非空时返回 {@code true}
     */
    public static boolean isNotEmpty(Collection<?> coll) {
        return !isEmpty(coll);
    }

    /**
     * 判断映射是否为 {@code null} 或没有条目。
     *
     * @param map 待检查映射
     * @return 映射为空时返回 {@code true}
     */
    public static boolean isEmpty(Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    /**
     * 判断映射是否至少包含一个条目。
     *
     * @param map 待检查映射
     * @return 映射非空时返回 {@code true}
     */
    public static boolean isNotEmpty(Map<?, ?> map) {
        return !isEmpty(map);
    }

    /**
     * 创建可变数组列表。
     *
     * @param elements 初始元素；数组为 {@code null} 时创建空列表
     * @param <T> 元素类型
     * @return 包含初始元素的可变列表
     */
    @SafeVarargs
    public static <T> List<T> newArrayList(T... elements) {
        List<T> result = new ArrayList<>();
        if (elements != null) {
            Collections.addAll(result, elements);
        }
        return result;
    }

    /**
     * 创建可变哈希集合。
     *
     * @param elements 初始元素；数组为 {@code null} 时创建空集合
     * @param <T> 元素类型
     * @return 包含初始元素的可变集合
     */
    @SafeVarargs
    public static <T> Set<T> newHashSet(T... elements) {
        Set<T> result = new HashSet<>();
        if (elements != null) {
            Collections.addAll(result, elements);
        }
        return result;
    }

    /**
     * 列表为空时返回调用方提供的默认列表。
     *
     * @param list 源列表
     * @param defaultList 默认列表
     * @param <T> 元素类型
     * @return 源列表非空时返回源列表，否则返回默认列表
     */
    public static <T> List<T> defaultIfEmpty(List<T> list, List<T> defaultList) {
        return isEmpty(list) ? defaultList : list;
    }

    /**
     * 将列表元素映射为独立的可变快照。
     *
     * @param list 源列表；为空时返回可变空列表
     * @param mapper 必填的元素映射函数
     * @param <T> 源元素类型
     * @param <R> 目标元素类型
     * @return 保持输入顺序的可变结果列表
     * @throws ValueOperationException 当映射函数为 {@code null} 时抛出
     */
    public static <T, R> List<R> extract(List<T> list, Function<T, R> mapper) {
        requireFunction(mapper, "mapper");
        List<R> result = new ArrayList<>();
        if (list != null) {
            for (T element : list) {
                result.add(mapper.apply(element));
            }
        }
        return result;
    }

    /**
     * 将列表转换为保持输入顺序的可变映射。
     *
     * <p>键冲突时保留第一次出现的元素。</p>
     *
     * @param list 源列表；为空时返回可变空映射
     * @param keyMapper 必填的键映射函数
     * @param <T> 源元素和值类型
     * @param <K> 键类型
     * @return 保持首次出现顺序的可变映射
     */
    public static <T, K> Map<K, T> toMap(List<T> list, Function<T, K> keyMapper) {
        return toMap(list, keyMapper, Function.identity());
    }

    /**
     * 将列表转换为自定义键值的可变映射。
     *
     * <p>键冲突时保留第一次出现的键值对。</p>
     *
     * @param list 源列表；为空时返回可变空映射
     * @param keyMapper 必填的键映射函数
     * @param valueMapper 必填的值映射函数
     * @param <T> 源元素类型
     * @param <K> 键类型
     * @param <V> 值类型
     * @return 保持首次出现顺序的可变映射
     * @throws ValueOperationException 当任一映射函数为 {@code null} 时抛出
     */
    public static <T, K, V> Map<K, V> toMap(
            List<T> list,
            Function<T, K> keyMapper,
            Function<T, V> valueMapper) {
        requireFunction(keyMapper, "keyMapper");
        requireFunction(valueMapper, "valueMapper");
        Map<K, V> result = new LinkedHashMap<>();
        if (list != null) {
            for (T element : list) {
                K key = keyMapper.apply(element);
                if (!result.containsKey(key)) {
                    result.put(key, valueMapper.apply(element));
                }
            }
        }
        return result;
    }

    /**
     * 计算两个集合的去重交集。
     *
     * @param first 第一个集合，决定结果顺序
     * @param second 第二个集合
     * @param <T> 元素类型
     * @return 按第一个集合首次出现顺序排列的可变列表
     */
    public static <T> List<T> intersection(Collection<T> first, Collection<T> second) {
        List<T> result = new ArrayList<>();
        if (isEmpty(first) || isEmpty(second)) {
            return result;
        }
        Set<T> secondValues = new HashSet<>(second);
        Set<T> accepted = new LinkedHashSet<>();
        for (T element : first) {
            if (secondValues.contains(element)) {
                accepted.add(element);
            }
        }
        result.addAll(accepted);
        return result;
    }

    /**
     * 计算两个集合的去重并集。
     *
     * @param first 第一个集合
     * @param second 第二个集合
     * @param <T> 元素类型
     * @return 按首次出现顺序排列的可变列表
     */
    public static <T> List<T> union(Collection<T> first, Collection<T> second) {
        Set<T> values = new LinkedHashSet<>();
        if (first != null) {
            values.addAll(first);
        }
        if (second != null) {
            values.addAll(second);
        }
        return new ArrayList<>(values);
    }

    /**
     * 计算第一个集合相对于第二个集合的去重差集。
     *
     * @param first 被减集合，决定结果顺序
     * @param second 减集合
     * @param <T> 元素类型
     * @return 按第一个集合首次出现顺序排列的可变列表
     */
    public static <T> List<T> subtract(Collection<T> first, Collection<T> second) {
        Set<T> values = new LinkedHashSet<>();
        if (first != null) {
            values.addAll(first);
        }
        if (second != null) {
            values.removeAll(second);
        }
        return new ArrayList<>(values);
    }

    /**
     * 将列表按指定大小切分为独立的可变子列表。
     *
     * @param list 源列表；为空时返回可变空列表
     * @param size 每个分片允许的最大元素数量，必须大于零
     * @param <T> 元素类型
     * @return 包含独立可变子列表的可变结果列表
     * @throws ValueOperationException 当分片大小小于等于零时抛出
     */
    public static <T> List<List<T>> partition(List<T> list, int size) {
        if (size <= 0) {
            throw ValueOperationException.invalidArgument("size");
        }
        List<List<T>> result = new ArrayList<>();
        if (list == null) {
            return result;
        }
        for (int index = 0; index < list.size(); index += size) {
            int endIndex = Math.min(index + size, list.size());
            result.add(new ArrayList<>(list.subList(index, endIndex)));
        }
        return result;
    }

    /**
     * 校验必填映射函数。
     *
     * @param function 待校验函数
     * @param parameterName 安全的参数名称
     */
    private static void requireFunction(Function<?, ?> function, String parameterName) {
        if (function == null) {
            throw ValueOperationException.invalidArgument(parameterName);
        }
    }
}

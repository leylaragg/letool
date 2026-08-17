package io.github.leylaragg.letool.datastructure.strategy;

import io.github.leylaragg.letool.datastructure.exception.DataStructureException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 按业务键保存策略实现的不可变有序注册表。
 *
 * <p>注册表在构建时复制策略映射，适合声明为 Spring 单例或普通共享对象。注册表结构可安全并发读取，
 * 但策略实例自身仍应由业务保证无状态或线程安全。本类型不限制策略接口签名，用户可以注册任意业务接口
 * 或函数式接口实现。</p>
 *
 * @param <K> 策略路由键类型
 * @param <S> 策略接口或实现类型
 */
public final class StrategyRegistry<K, S> {

    /** 保持注册顺序且不可修改的策略映射。 */
    private final Map<K, S> strategies;

    /** 保持注册顺序且不可修改的策略键快照。 */
    private final Set<K> keys;

    /**
     * 从已经校验的构建器状态创建不可变注册表。
     *
     * @param source 已校验的可变策略映射
     */
    private StrategyRegistry(Map<K, S> source) {
        this.strategies = Collections.unmodifiableMap(new LinkedHashMap<>(source));
        this.keys = Collections.unmodifiableSet(new LinkedHashSet<>(this.strategies.keySet()));
    }

    /**
     * 创建空策略注册表构建器。
     *
     * @param <K> 策略路由键类型
     * @param <S> 策略接口或实现类型
     * @return 新的空构建器
     */
    public static <K, S> Builder<K, S> builder() {
        return new Builder<>();
    }

    /**
     * 从已有注册表创建可修改副本。
     *
     * @param source 已有不可变注册表
     * @param <K> 策略路由键类型
     * @param <S> 策略接口或实现类型
     * @return 预装已有策略的新构建器
     * @throws DataStructureException 当已有注册表为 {@code null} 时抛出
     */
    public static <K, S> Builder<K, S> builder(StrategyRegistry<K, S> source) {
        if (source == null) {
            throw DataStructureException.invalidArgument("sourceRegistry");
        }
        return new Builder<>(source.strategies);
    }

    /**
     * 可选查找指定键对应的策略。
     *
     * @param key 策略路由键
     * @return 键为空或未命中时返回空，否则返回策略实例
     */
    public Optional<S> find(K key) {
        return isMissingLookupKey(key) ? Optional.empty() : Optional.ofNullable(strategies.get(key));
    }

    /**
     * 获取指定键对应的必需策略。
     *
     * @param key 策略路由键
     * @return 已注册策略实例
     * @throws DataStructureException 当键为空或策略不存在时抛出
     */
    public S getRequired(K key) {
        return find(key).orElseThrow(
                () -> DataStructureException.strategyNotFound("strategyKey")
        );
    }

    /**
     * 判断指定键是否已经注册。
     *
     * @param key 策略路由键
     * @return 键有效且已经注册时返回 {@code true}
     */
    public boolean contains(K key) {
        return !isMissingLookupKey(key) && strategies.containsKey(key);
    }

    /**
     * 获取保持注册顺序的不可变策略键快照。
     *
     * @return 不可修改的策略键集合
     */
    public Set<K> keys() {
        return keys;
    }

    /**
     * 获取保持注册顺序的不可变策略映射。
     *
     * @return 不可修改的策略映射
     */
    public Map<K, S> asMap() {
        return strategies;
    }

    /**
     * 获取已注册策略数量。
     *
     * @return 非负策略数量
     */
    public int size() {
        return strategies.size();
    }

    /**
     * 判断注册表是否没有策略。
     *
     * @return 没有注册任何策略时返回 {@code true}
     */
    public boolean isEmpty() {
        return strategies.isEmpty();
    }

    /**
     * 判断查询键是否应按缺失处理。
     *
     * @param key 待检查策略键
     * @return 键为 {@code null} 或空白字符序列时返回 {@code true}
     */
    private static boolean isMissingLookupKey(Object key) {
        return key == null || key instanceof CharSequence sequence && sequence.toString().isBlank();
    }

    /**
     * 策略注册表构建器。
     *
     * <p>{@link #register(Object, Object)} 默认拒绝覆盖，替换默认策略必须显式调用
     * {@link #replace(Object, Object)}，避免拼写错误或重复扫描造成静默覆盖。</p>
     *
     * @param <K> 策略路由键类型
     * @param <S> 策略接口或实现类型
     */
    public static final class Builder<K, S> {

        /** 按注册顺序保存构建中的策略。 */
        private final Map<K, S> strategies;

        /**
         * 创建空构建器。
         */
        private Builder() {
            this.strategies = new LinkedHashMap<>();
        }

        /**
         * 使用已有策略快照创建构建器。
         *
         * @param source 已有策略映射
         */
        private Builder(Map<K, S> source) {
            this.strategies = new LinkedHashMap<>(source);
        }

        /**
         * 注册一个新的策略键和值。
         *
         * @param key 非空策略路由键；字符序列不能只包含空白
         * @param strategy 非空策略实例
         * @return 当前构建器
         * @throws DataStructureException 当参数无效或键已经存在时抛出
         */
        public Builder<K, S> register(K key, S strategy) {
            requireRegistration(key, strategy);
            if (strategies.containsKey(key)) {
                throw DataStructureException.duplicateStrategyKey("strategyKey");
            }
            strategies.put(key, strategy);
            return this;
        }

        /**
         * 按映射迭代顺序原子批量注册策略。
         *
         * <p>全部条目校验通过后才写入构建器，任一条目无效或冲突时不会保留部分结果。</p>
         *
         * @param source 待注册策略映射
         * @return 当前构建器
         * @throws DataStructureException 当映射为空引用、条目无效或键冲突时抛出
         */
        public Builder<K, S> registerAll(Map<? extends K, ? extends S> source) {
            if (source == null) {
                throw DataStructureException.invalidArgument("strategies");
            }
            Map<K, S> additions = new LinkedHashMap<>();
            for (Map.Entry<? extends K, ? extends S> entry : source.entrySet()) {
                K key = entry.getKey();
                S strategy = entry.getValue();
                requireRegistration(key, strategy);
                if (strategies.containsKey(key) || additions.containsKey(key)) {
                    throw DataStructureException.duplicateStrategyKey("strategyKey");
                }
                additions.put(key, strategy);
            }
            strategies.putAll(additions);
            return this;
        }

        /**
         * 显式替换已经存在的策略。
         *
         * @param key 已存在的策略路由键
         * @param strategy 新的非空策略实例
         * @return 当前构建器
         * @throws DataStructureException 当参数无效或待替换键不存在时抛出
         */
        public Builder<K, S> replace(K key, S strategy) {
            requireRegistration(key, strategy);
            if (!strategies.containsKey(key)) {
                throw DataStructureException.strategyNotFound("strategyKey");
            }
            strategies.put(key, strategy);
            return this;
        }

        /**
         * 构建与当前构建器后续变更隔离的不可变注册表。
         *
         * @return 不可变有序策略注册表
         */
        public StrategyRegistry<K, S> build() {
            return new StrategyRegistry<>(strategies);
        }

        /**
         * 校验注册键和策略实例。
         *
         * @param key 待注册策略键
         * @param strategy 待注册策略实例
         */
        private static void requireRegistration(Object key, Object strategy) {
            if (isMissingLookupKey(key)) {
                throw DataStructureException.invalidArgument("strategyKey");
            }
            if (strategy == null) {
                throw DataStructureException.invalidArgument("strategy");
            }
        }
    }
}

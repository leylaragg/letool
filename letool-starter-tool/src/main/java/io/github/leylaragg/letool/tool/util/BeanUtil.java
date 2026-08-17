package io.github.leylaragg.letool.tool.util;

import io.github.leylaragg.letool.tool.reflection.ReflectionOperationException;
import org.springframework.beans.BeanUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 基于 Spring BeanUtils 的 Bean 实例化、属性拷贝和批量转换便捷工具。
 *
 * <p>工具只处理同名且类型兼容的 JavaBean 属性，不提供深拷贝、循环引用复制或自动类型转换。
 * 高频且类型固定的大规模对象映射应使用 MapStruct 等编译期映射方案。</p>
 */
public final class BeanUtil {

    /** 工具类不允许实例化。 */
    private BeanUtil() {
    }

    /**
     * 拷贝对象属性到由目标类型创建的新实例。
     *
     * @param source 源对象，允许为空
     * @param targetClass 目标类型，必须能够由 Spring BeanUtils 实例化
     * @param <S> 源对象类型
     * @param <T> 目标对象类型
     * @return 完成属性拷贝的目标对象；源对象为空时返回 {@code null}
     * @throws ReflectionOperationException 目标类型无效、无法实例化或属性拷贝失败时抛出
     */
    public static <S, T> T copy(S source, Class<T> targetClass) {
        return copy(source, targetClass, new String[0]);
    }

    /**
     * 拷贝对象属性到新实例，并忽略指定目标属性。
     *
     * @param source 源对象，允许为空
     * @param targetClass 目标类型，必须能够由 Spring BeanUtils 实例化
     * @param ignoreProperties 不参与拷贝的目标属性名称
     * @param <S> 源对象类型
     * @param <T> 目标对象类型
     * @return 完成属性拷贝的目标对象；源对象为空时返回 {@code null}
     * @throws ReflectionOperationException 参数无效、无法实例化或属性拷贝失败时抛出
     */
    public static <S, T> T copy(
            S source,
            Class<T> targetClass,
            String... ignoreProperties) {
        requireType(targetClass, "targetClass");
        String[] ignored = normalizeIgnoreProperties(ignoreProperties);
        if (source == null) {
            return null;
        }
        T target = newInstance(targetClass);
        copyProperties(source, target, ignored);
        return target;
    }

    /**
     * 使用调用方提供的工厂创建目标实例后拷贝属性。
     *
     * <p>该入口适用于没有默认构造器、需要 Builder 或必须由受控工厂创建的目标对象。</p>
     *
     * @param source 源对象，允许为空
     * @param targetSupplier 目标对象工厂，返回值不得为空
     * @param <S> 源对象类型
     * @param <T> 目标对象类型
     * @return 完成属性拷贝的目标对象；源对象为空时返回 {@code null}
     * @throws ReflectionOperationException 工厂无效、目标创建失败或属性拷贝失败时抛出
     */
    public static <S, T> T copy(S source, Supplier<T> targetSupplier) {
        if (targetSupplier == null) {
            throw ReflectionOperationException.invalidArgument("targetSupplier");
        }
        if (source == null) {
            return null;
        }

        T target;
        try {
            target = targetSupplier.get();
        } catch (RuntimeException exception) {
            throw ReflectionOperationException.instantiationFailed(null, exception);
        }
        if (target == null) {
            throw ReflectionOperationException.instantiationFailed(
                    null,
                    new IllegalStateException("Target supplier returned null")
            );
        }
        copyProperties(source, target);
        return target;
    }

    /**
     * 将源对象的兼容属性原地拷贝到目标对象。
     *
     * @param source 源对象，允许为空；为空时不修改目标对象
     * @param target 目标对象，不得为空
     * @param <S> 源对象类型
     * @param <T> 目标对象类型
     * @throws ReflectionOperationException 目标为空或属性拷贝失败时抛出
     */
    public static <S, T> void copyProperties(S source, T target) {
        copyProperties(source, target, new String[0]);
    }

    /**
     * 将源对象的兼容属性原地拷贝到目标对象，并忽略指定属性。
     *
     * @param source 源对象，允许为空；为空时不修改目标对象
     * @param target 目标对象，不得为空
     * @param ignoreProperties 不参与拷贝的目标属性名称
     * @param <S> 源对象类型
     * @param <T> 目标对象类型
     * @throws ReflectionOperationException 参数无效或属性拷贝失败时抛出
     */
    public static <S, T> void copyProperties(
            S source,
            T target,
            String... ignoreProperties) {
        if (target == null) {
            throw ReflectionOperationException.invalidArgument("target");
        }
        String[] ignored = normalizeIgnoreProperties(ignoreProperties);
        if (source == null) {
            return;
        }

        try {
            BeanUtils.copyProperties(source, target, ignored);
        } catch (RuntimeException exception) {
            String mapping = source.getClass().getName() + "->" + target.getClass().getName();
            throw ReflectionOperationException.fieldAccessFailed(mapping, exception);
        }
    }

    /**
     * 兼容旧版所谓高性能拷贝入口。
     *
     * <p>该方法不再维护 CGLIB BeanCopier 缓存，统一委托可靠的 Spring BeanUtils。
     * 高频固定类型映射应迁移到 MapStruct。</p>
     *
     * @param source 源对象，允许为空
     * @param targetClass 目标类型
     * @param <S> 源对象类型
     * @param <T> 目标对象类型
     * @return 完成属性拷贝的目标对象；源对象为空时返回 {@code null}
     * @deprecated 使用 {@link #copy(Object, Class)} 或编译期映射方案
     */
    @Deprecated(since = "2.0.0", forRemoval = true)
    public static <S, T> T copyFast(S source, Class<T> targetClass) {
        return copy(source, targetClass);
    }

    /**
     * 按输入顺序批量拷贝列表。
     *
     * <p>返回值始终是可修改的新列表，输入中的 {@code null} 元素会在对应位置保留。</p>
     *
     * @param sourceList 源列表，允许为空
     * @param targetClass 目标类型
     * @param <S> 源对象类型
     * @param <T> 目标对象类型
     * @return 可修改的新列表
     * @throws ReflectionOperationException 目标类型无效或任一元素转换失败时抛出
     */
    public static <S, T> List<T> copyList(List<S> sourceList, Class<T> targetClass) {
        requireType(targetClass, "targetClass");
        if (sourceList == null || sourceList.isEmpty()) {
            return new ArrayList<>();
        }

        List<T> targets = new ArrayList<>(sourceList.size());
        for (S source : sourceList) {
            targets.add(copy(source, targetClass));
        }
        return targets;
    }

    /**
     * 兼容旧版所谓高性能批量拷贝入口。
     *
     * @param sourceList 源列表，允许为空
     * @param targetClass 目标类型
     * @param <S> 源对象类型
     * @param <T> 目标对象类型
     * @return 可修改的新列表
     * @deprecated 使用 {@link #copyList(List, Class)} 或编译期映射方案
     */
    @Deprecated(since = "2.0.0", forRemoval = true)
    public static <S, T> List<T> copyListFast(List<S> sourceList, Class<T> targetClass) {
        return copyList(sourceList, targetClass);
    }

    /**
     * 将 JavaBean 转换为键值 Map。
     *
     * @param object 待转换对象，允许为空
     * @return 字段名到值的 Map；对象为空时返回 {@code null}
     */
    public static Map<String, Object> toMap(Object object) {
        return object == null ? null : JsonUtil.toMap(object);
    }

    /**
     * 将键值 Map 转换为目标 JavaBean。
     *
     * @param map 字段名到值的 Map，允许为空
     * @param targetClass 目标类型
     * @param <T> 目标对象类型
     * @return 转换后的对象；Map 为空时返回 {@code null}
     * @throws ReflectionOperationException 目标类型无效时抛出
     */
    public static <T> T toBean(Map<String, Object> map, Class<T> targetClass) {
        requireType(targetClass, "targetClass");
        return map == null ? null : JsonUtil.toBean(map, targetClass);
    }

    /**
     * 使用 Spring BeanUtils 创建目标类型实例。
     *
     * @param clazz 目标类型
     * @param <T> 目标对象类型
     * @return 新实例
     * @throws ReflectionOperationException 类型无效或实例化失败时抛出
     */
    public static <T> T newInstance(Class<T> clazz) {
        requireType(clazz, "clazz");
        try {
            return BeanUtils.instantiateClass(clazz);
        } catch (RuntimeException exception) {
            throw ReflectionOperationException.instantiationFailed(clazz, exception);
        }
    }

    /**
     * 校验目标类型控制参数。
     *
     * @param type 待校验类型
     * @param parameterName 公开参数名称
     */
    private static void requireType(Class<?> type, String parameterName) {
        if (type == null) {
            throw ReflectionOperationException.invalidArgument(parameterName);
        }
    }

    /**
     * 校验并复制忽略属性数组，避免调用期间被外部修改。
     *
     * @param ignoreProperties 调用方属性名称数组
     * @return 非空属性名称数组副本
     */
    private static String[] normalizeIgnoreProperties(String[] ignoreProperties) {
        if (ignoreProperties == null || ignoreProperties.length == 0) {
            return new String[0];
        }
        String[] copy = ignoreProperties.clone();
        for (String property : copy) {
            if (property == null || property.isBlank()) {
                throw ReflectionOperationException.invalidArgument("ignoreProperties");
            }
        }
        return copy;
    }
}

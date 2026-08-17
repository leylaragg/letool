package io.github.leylaragg.letool.tool.util;

import io.github.leylaragg.letool.tool.enums.CodeEnum;
import io.github.leylaragg.letool.tool.enums.DescribedEnum;
import io.github.leylaragg.letool.tool.value.ValueOperationException;
import org.springframework.beans.BeanUtils;
import org.springframework.util.ReflectionUtils;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 提供业务枚举按名称、编码或属性查找，以及生成前端选项映射的能力。
 *
 * <p>推荐枚举实现 {@link CodeEnum} 和 {@link DescribedEnum}，工具会直接使用接口而不执行反射。
 * 为兼容历史项目，未实现接口的枚举仍可通过 JavaBean Getter 或同名字段访问；属性元数据按枚举类型
 * 缓存，读取失败会转换为稳定异常，不再静默返回空值。</p>
 */
public final class EnumUtil {

    /** 枚举属性元数据缓存，不持有具体枚举实例。 */
    private static final ClassValue<EnumMetadata> METADATA_CACHE = new ClassValue<>() {
        /**
         * 为指定枚举类型创建属性元数据。
         *
         * @param type 枚举类型
         * @return 不可变属性元数据
         */
        @Override
        protected EnumMetadata computeValue(Class<?> type) {
            return EnumMetadata.create(type);
        }
    };

    /**
     * 禁止创建工具类实例。
     */
    private EnumUtil() {
    }

    /**
     * 按枚举常量名称查找。
     *
     * @param enumClass 枚举类型
     * @param name 枚举常量名称，大小写敏感
     * @param <E> 枚举类型
     * @return 查找结果；名称为空或未命中时返回空
     */
    public static <E extends Enum<E>> Optional<E> findByName(Class<E> enumClass, String name) {
        requireEnumClass(enumClass);
        if (name == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Enum.valueOf(enumClass, name));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    /**
     * 按业务编码查找枚举常量。
     *
     * @param enumClass 枚举类型
     * @param code 期望业务编码
     * @param <E> 枚举类型
     * @return 查找结果；编码为空或未命中时返回空
     */
    public static <E extends Enum<E>> Optional<E> findByCode(Class<E> enumClass, Object code) {
        requireEnumClass(enumClass);
        if (code == null) {
            return Optional.empty();
        }
        for (E constant : enumClass.getEnumConstants()) {
            if (Objects.equals(code, readCode(constant))) {
                return Optional.of(constant);
            }
        }
        return Optional.empty();
    }

    /**
     * 按任意 JavaBean 属性或字段值查找枚举常量。
     *
     * @param enumClass 枚举类型
     * @param propertyName 属性或字段名称
     * @param value 期望属性值
     * @param <E> 枚举类型
     * @return 查找结果；期望值为空或未命中时返回空
     * @throws ValueOperationException 当属性名称无效、属性不存在或读取失败时抛出
     */
    public static <E extends Enum<E>> Optional<E> findBy(
            Class<E> enumClass,
            String propertyName,
            Object value) {
        requireEnumClass(enumClass);
        ValueReader reader = requireReader(enumClass, propertyName);
        if (value == null) {
            return Optional.empty();
        }
        for (E constant : enumClass.getEnumConstants()) {
            if (Objects.equals(value, reader.read(constant))) {
                return Optional.of(constant);
            }
        }
        return Optional.empty();
    }

    /**
     * 按名称查找枚举常量并兼容历史空值返回契约。
     *
     * @param enumClass 枚举类型
     * @param name 枚举常量名称
     * @param <E> 枚举类型
     * @return 命中的枚举常量；未命中时返回 {@code null}
     */
    public static <E extends Enum<E>> E getByName(Class<E> enumClass, String name) {
        return findByName(enumClass, name).orElse(null);
    }

    /**
     * 按业务编码查找枚举常量并兼容历史空值返回契约。
     *
     * @param enumClass 枚举类型
     * @param code 期望业务编码
     * @param <E> 枚举类型
     * @return 命中的枚举常量；未命中时返回 {@code null}
     */
    public static <E extends Enum<E>> E getByCode(Class<E> enumClass, Object code) {
        return findByCode(enumClass, code).orElse(null);
    }

    /**
     * 按任意属性查找枚举常量并兼容历史空值返回契约。
     *
     * @param enumClass 枚举类型
     * @param propertyName 属性或字段名称
     * @param value 期望属性值
     * @param <E> 枚举类型
     * @return 命中的枚举常量；未命中时返回 {@code null}
     */
    public static <E extends Enum<E>> E getBy(
            Class<E> enumClass,
            String propertyName,
            Object value) {
        return findBy(enumClass, propertyName, value).orElse(null);
    }

    /**
     * 按业务编码严格查找枚举常量。
     *
     * @param enumClass 枚举类型
     * @param code 期望业务编码
     * @param <E> 枚举类型
     * @return 命中的枚举常量
     * @throws ValueOperationException 当未找到对应枚举常量时抛出
     */
    public static <E extends Enum<E>> E requireByCode(Class<E> enumClass, Object code) {
        requireEnumClass(enumClass);
        return findByCode(enumClass, code).orElseThrow(
                () -> ValueOperationException.enumConstantNotFound(enumClass.getSimpleName())
        );
    }

    /**
     * 将枚举转换为保持声明顺序的描述与编码映射。
     *
     * <p>实现业务契约时分别读取 {@link DescribedEnum#getDescription()} 和
     * {@link CodeEnum#getCode()}。历史枚举优先读取同名 Getter 或字段，属性不存在或值为空时
     * 降级为枚举常量名称。描述重复会抛出异常，避免业务选项被静默覆盖。</p>
     *
     * @param enumClass 枚举类型
     * @param <E> 枚举类型
     * @return 描述到编码的可变有序映射
     * @throws ValueOperationException 当描述重复或属性读取失败时抛出
     */
    public static <E extends Enum<E>> Map<String, Object> toMap(Class<E> enumClass) {
        requireEnumClass(enumClass);
        Map<String, Object> result = new LinkedHashMap<>();
        for (E constant : enumClass.getEnumConstants()) {
            String label = readDescription(constant);
            if (result.containsKey(label)) {
                throw ValueOperationException.duplicateEnumLabel("description");
            }
            result.put(label, readCodeOrName(constant));
        }
        return result;
    }

    /**
     * 读取枚举业务编码，标准契约优先于历史反射访问。
     *
     * @param constant 枚举常量
     * @return 业务编码；历史枚举必须存在 {@code code} 属性
     */
    private static Object readCode(Enum<?> constant) {
        if (constant instanceof CodeEnum<?> codeEnum) {
            try {
                return codeEnum.getCode();
            } catch (RuntimeException exception) {
                throw ValueOperationException.enumAccessFailed("code", exception);
            }
        }
        return requireReader(constant.getDeclaringClass(), "code").read(constant);
    }

    /**
     * 读取用于选项映射的枚举业务编码。
     *
     * @param constant 枚举常量
     * @return 业务编码；历史枚举未定义编码时返回枚举名称
     */
    private static Object readCodeOrName(Enum<?> constant) {
        if (constant instanceof CodeEnum<?>) {
            Object code = readCode(constant);
            return code == null ? constant.name() : code;
        }
        return findReader(constant.getDeclaringClass(), "code")
                .map(reader -> reader.read(constant))
                .orElse(constant.name());
    }

    /**
     * 读取用于选项映射的枚举描述。
     *
     * @param constant 枚举常量
     * @return 展示描述；历史枚举未定义描述时返回枚举名称
     */
    private static String readDescription(Enum<?> constant) {
        Object description;
        if (constant instanceof DescribedEnum describedEnum) {
            try {
                description = describedEnum.getDescription();
            } catch (RuntimeException exception) {
                throw ValueOperationException.enumAccessFailed("description", exception);
            }
        } else {
            description = findReader(constant.getDeclaringClass(), "description")
                    .map(reader -> reader.read(constant))
                    .orElse(constant.name());
        }
        return description == null ? constant.name() : String.valueOf(description);
    }

    /**
     * 获取指定枚举属性读取器。
     *
     * @param enumClass 枚举类型
     * @param propertyName 属性名称
     * @return 属性读取器
     * @throws ValueOperationException 当属性名称无效或属性不存在时抛出
     */
    private static ValueReader requireReader(Class<?> enumClass, String propertyName) {
        if (propertyName == null || propertyName.isBlank()) {
            throw ValueOperationException.invalidArgument("propertyName");
        }
        return findReader(enumClass, propertyName).orElseThrow(
                () -> ValueOperationException.enumAccessFailed(
                        propertyName,
                        new NoSuchFieldException(propertyName)
                )
        );
    }

    /**
     * 查找指定枚举属性读取器。
     *
     * @param enumClass 枚举类型
     * @param propertyName 属性名称
     * @return 属性读取器；不存在时返回空
     */
    private static Optional<ValueReader> findReader(Class<?> enumClass, String propertyName) {
        return Optional.ofNullable(METADATA_CACHE.get(enumClass).readers().get(propertyName));
    }

    /**
     * 校验枚举类型参数。
     *
     * @param enumClass 待校验类型
     */
    private static void requireEnumClass(Class<?> enumClass) {
        if (enumClass == null || !enumClass.isEnum()) {
            throw ValueOperationException.invalidArgument("enumClass");
        }
    }

    /**
     * 枚举属性读取函数。
     */
    @FunctionalInterface
    private interface ValueReader {

        /**
         * 读取指定枚举常量的属性值。
         *
         * @param source 枚举常量
         * @return 属性值
         */
        Object read(Object source);
    }

    /**
     * 按枚举类型缓存的不可变属性读取元数据。
     *
     * @param readers 属性名称与读取器映射
     */
    private record EnumMetadata(Map<String, ValueReader> readers) {

        /**
         * 扫描 JavaBean Getter 和私有字段并创建元数据。
         *
         * @param enumClass 枚举类型
         * @return 不可变属性元数据
         */
        private static EnumMetadata create(Class<?> enumClass) {
            Map<String, ValueReader> readers = new LinkedHashMap<>();
            for (PropertyDescriptor descriptor : BeanUtils.getPropertyDescriptors(enumClass)) {
                Method method = descriptor.getReadMethod();
                if (method != null && !"class".equals(descriptor.getName())) {
                    readers.put(descriptor.getName(), methodReader(descriptor.getName(), method));
                }
            }
            for (Class<?> current = enumClass;
                    current != null && current != Enum.class;
                    current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    if (!field.isSynthetic()) {
                        readers.putIfAbsent(field.getName(), fieldReader(field.getName(), field));
                    }
                }
            }
            return new EnumMetadata(Collections.unmodifiableMap(readers));
        }

        /**
         * 创建方法属性读取器。
         *
         * @param propertyName 属性名称
         * @param method Getter 方法
         * @return 方法读取器
         */
        private static ValueReader methodReader(String propertyName, Method method) {
            return source -> {
                try {
                    ReflectionUtils.makeAccessible(method);
                    return ReflectionUtils.invokeMethod(method, source);
                } catch (RuntimeException exception) {
                    throw ValueOperationException.enumAccessFailed(propertyName, exception);
                }
            };
        }

        /**
         * 创建字段属性读取器。
         *
         * @param propertyName 属性名称
         * @param field 私有字段
         * @return 字段读取器
         */
        private static ValueReader fieldReader(String propertyName, Field field) {
            return source -> {
                try {
                    ReflectionUtils.makeAccessible(field);
                    return ReflectionUtils.getField(field, source);
                } catch (RuntimeException exception) {
                    throw ValueOperationException.enumAccessFailed(propertyName, exception);
                }
            };
        }
    }
}

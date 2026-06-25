package com.github.leyland.letool.sensitive.core;

import com.github.leyland.letool.sensitive.annotation.Sensitive;
import com.github.leyland.letool.sensitive.strategy.*;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 脱敏处理器 —— 将 {@link SensitiveType} 路由到对应的 {@link SensitiveStrategy}，提供三种粒度的脱敏入口.
 *
 * <h3>使用层级</h3>
 * <pre>
 *   {@link #mask(String, SensitiveType)}              单值脱敏，使用策略默认参数
 *   {@link #mask(String, SensitiveType, MaskContext)}  单值脱敏，完整控制遮盖规则
 *   {@link #mask(Object)}                              对象脱敏，反射扫描 @Sensitive 字段
 * </pre>
 *
 * <h3>策略注册机制</h3>
 * <p>19 种内置策略在 static 块中注册到 {@link #STRATEGIES}（ConcurrentHashMap）。
 * 第三方可通过 {@link #register(SensitiveType, SensitiveStrategy)} 注册自定义策略，覆盖内置实现。</p>
 */
public class SensitiveProcessor {

    /**
     * 策略注册表 —— key=SensitiveType 枚举值，value=对应的脱敏策略实现。
     * ConcurrentHashMap 保证并发注册和查询的线程安全。
     */
    private static final Map<SensitiveType, SensitiveStrategy<MaskContext>> STRATEGIES = new ConcurrentHashMap<>();

    static {
        register(SensitiveType.PHONE, new PhoneSensitiveStrategy());
        register(SensitiveType.ID_CARD, new IdCardSensitiveStrategy());
        register(SensitiveType.NAME, new NameSensitiveStrategy());
        register(SensitiveType.EMAIL, new EmailSensitiveStrategy());
        register(SensitiveType.BANK_CARD, new BankCardSensitiveStrategy());
        register(SensitiveType.ADDRESS, new AddressSensitiveStrategy());
        register(SensitiveType.PASSWORD, new PasswordSensitiveStrategy());
        register(SensitiveType.CAR_LICENSE, new CarLicenseSensitiveStrategy());
        register(SensitiveType.FIXED_PHONE, new FixedPhoneSensitiveStrategy());
        register(SensitiveType.IPV4, new Ipv4SensitiveStrategy());
        register(SensitiveType.IPV6, new Ipv6SensitiveStrategy());
        register(SensitiveType.WECHAT, new WechatSensitiveStrategy());
        register(SensitiveType.QQ, new QqSensitiveStrategy());
        register(SensitiveType.PASSPORT, new PassportSensitiveStrategy());
        register(SensitiveType.DOM, new DomSensitiveStrategy());
        register(SensitiveType.POSITION, new PositionSensitiveStrategy());
        register(SensitiveType.KEEP_LENGTH, new KeepLengthSensitiveStrategy());
        register(SensitiveType.TAIL_DISPLAY, new TailDisplaySensitiveStrategy());
        register(SensitiveType.CUSTOM, new RegexSensitiveStrategy());
    }

    private SensitiveProcessor() {}

    /**
     * 注册或覆盖策略 —— 支持第三方通过 SPI 或手动调用扩展自定义脱敏规则。
     * 如果 type 已存在注册，会覆盖旧策略。
     */
    public static <C> void register(SensitiveType type, SensitiveStrategy<C> strategy) {
        @SuppressWarnings("unchecked")
        SensitiveStrategy<MaskContext> cast = (SensitiveStrategy<MaskContext>) strategy;
        STRATEGIES.put(type, cast);
    }

    /** 从 @Sensitive 注解提取参数后脱敏 —— Jackson 序列化器使用此入口. */
    public static String mask(String value, Sensitive annotation) {
        if (value == null || value.isEmpty()) return value;
        SensitiveStrategy<MaskContext> strategy = STRATEGIES.get(annotation.type());
        if (strategy == null) return value;
        MaskContext ctx = MaskContext.from(annotation);
        return strategy.mask(value, ctx);
    }

    /** 按类型 + 默认参数脱敏 —— 最常见的调用方式. */
    public static String mask(String value, SensitiveType type) {
        if (value == null || value.isEmpty()) return value;
        SensitiveStrategy<MaskContext> strategy = STRATEGIES.get(type);
        if (strategy == null) return value;
        return strategy.mask(value, MaskContext.DEFAULT);
    }

    /** 按类型 + 自定义 Context 脱敏 —— 需要覆盖 keepPrefix/maskChar 等默认值时使用. */
    public static String mask(String value, SensitiveType type, MaskContext context) {
        if (value == null || value.isEmpty()) return value;
        SensitiveStrategy<MaskContext> strategy = STRATEGIES.get(type);
        if (strategy == null) return value;
        return strategy.mask(value, context);
    }

    /**
     * 反射扫描对象中所有标注 @Sensitive 的 String 字段并脱敏，
     * 返回克隆后的新对象（不修改原对象）。
     *
     * <p>注意：要求目标类有无参构造器，否则回退返回原对象。</p>
     */
    @SuppressWarnings("unchecked")
    public static <T> T mask(T object) {
        if (object == null) return null;
        try {
            // 无参构造克隆对象，避免修改原对象
            T clone = (T) object.getClass().getDeclaredConstructor().newInstance();
            for (Field field : object.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(object);
                // 仅处理标注了 @Sensitive 的 String 类型字段
                Sensitive annotation = field.getAnnotation(Sensitive.class);
                if (annotation != null && value instanceof String) {
                    value = mask((String) value, annotation);
                }
                Field cloneField = object.getClass().getDeclaredField(field.getName());
                cloneField.setAccessible(true);
                cloneField.set(clone, value);
            }
            return clone;
        } catch (Exception e) {
            // 无参构造缺失或字段不可访问时，回退返回原对象
            return object;
        }
    }

    /** 获取指定类型的策略实例，可用于运行时判断策略是否已注册. */
    public static SensitiveStrategy<MaskContext> getStrategy(SensitiveType type) {
        return STRATEGIES.get(type);
    }

    /** 返回当前所有已注册策略的不可变快照. */
    public static Map<SensitiveType, SensitiveStrategy<MaskContext>> getRegisteredStrategies() {
        return Map.copyOf(STRATEGIES);
    }
}

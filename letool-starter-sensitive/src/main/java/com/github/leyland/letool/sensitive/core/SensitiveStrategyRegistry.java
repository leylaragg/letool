package com.github.leyland.letool.sensitive.core;

import com.github.leyland.letool.sensitive.exception.SensitiveException;
import com.github.leyland.letool.sensitive.strategy.AddressSensitiveStrategy;
import com.github.leyland.letool.sensitive.strategy.BankCardSensitiveStrategy;
import com.github.leyland.letool.sensitive.strategy.CarLicenseSensitiveStrategy;
import com.github.leyland.letool.sensitive.strategy.DomSensitiveStrategy;
import com.github.leyland.letool.sensitive.strategy.EmailSensitiveStrategy;
import com.github.leyland.letool.sensitive.strategy.FixedPhoneSensitiveStrategy;
import com.github.leyland.letool.sensitive.strategy.IdCardSensitiveStrategy;
import com.github.leyland.letool.sensitive.strategy.Ipv4SensitiveStrategy;
import com.github.leyland.letool.sensitive.strategy.Ipv6SensitiveStrategy;
import com.github.leyland.letool.sensitive.strategy.KeepLengthSensitiveStrategy;
import com.github.leyland.letool.sensitive.strategy.NameSensitiveStrategy;
import com.github.leyland.letool.sensitive.strategy.PassportSensitiveStrategy;
import com.github.leyland.letool.sensitive.strategy.PasswordSensitiveStrategy;
import com.github.leyland.letool.sensitive.strategy.PhoneSensitiveStrategy;
import com.github.leyland.letool.sensitive.strategy.PositionSensitiveStrategy;
import com.github.leyland.letool.sensitive.strategy.QqSensitiveStrategy;
import com.github.leyland.letool.sensitive.strategy.RegexSensitiveStrategy;
import com.github.leyland.letool.sensitive.strategy.TailDisplaySensitiveStrategy;
import com.github.leyland.letool.sensitive.strategy.WechatSensitiveStrategy;

import java.util.EnumMap;
import java.util.Map;

/**
 * 不可变脱敏策略注册表。
 *
 * <p>默认注册表包含全部内置策略。用户可以通过 {@link #builder()} 覆盖某个类型，
 * 再将构建结果声明为 Spring Bean；不同注册表实例互不影响，不存在全局注册污染。</p>
 */
public final class SensitiveStrategyRegistry {

    private static final SensitiveStrategyRegistry DEFAULTS = builder().build();

    private final Map<SensitiveType, SensitiveStrategy<MaskContext>> strategies;

    /**
     * 创建不可变策略注册表。
     *
     * @param strategies 已完成校验的策略集合
     */
    private SensitiveStrategyRegistry(Map<SensitiveType, SensitiveStrategy<MaskContext>> strategies) {
        this.strategies = Map.copyOf(strategies);
    }

    /**
     * 获取包含全部内置策略的共享注册表。
     *
     * @return 不可变默认策略注册表
     */
    public static SensitiveStrategyRegistry defaults() {
        return DEFAULTS;
    }

    /**
     * 创建预装全部内置策略的构建器。
     *
     * @return 策略注册表构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取指定类型的策略。
     *
     * @param type 脱敏类型
     * @return 已注册策略
     * @throws SensitiveException 类型为空或策略不存在时抛出
     */
    public SensitiveStrategy<MaskContext> getRequired(SensitiveType type) {
        if (type == null) {
            throw SensitiveException.configurationInvalid("脱敏类型不能为空");
        }
        SensitiveStrategy<MaskContext> strategy = strategies.get(type);
        if (strategy == null) {
            throw SensitiveException.strategyNotFound(type);
        }
        return strategy;
    }

    /**
     * 获取全部策略的不可变视图。
     *
     * @return 类型与策略的不可变映射
     */
    public Map<SensitiveType, SensitiveStrategy<MaskContext>> asMap() {
        return strategies;
    }

    /**
     * 策略注册表构建器。
     */
    public static final class Builder {

        private final EnumMap<SensitiveType, SensitiveStrategy<MaskContext>> strategies =
                new EnumMap<>(SensitiveType.class);

        /**
         * 创建并预装所有内置策略。
         */
        private Builder() {
            registerBuiltIns();
        }

        /**
         * 注册或覆盖指定类型的脱敏策略。
         *
         * @param type 脱敏类型
         * @param strategy 无状态且线程安全的脱敏策略
         * @return 当前构建器
         */
        public Builder register(SensitiveType type, SensitiveStrategy<MaskContext> strategy) {
            if (type == null) {
                throw SensitiveException.configurationInvalid("脱敏类型不能为空");
            }
            if (strategy == null) {
                throw SensitiveException.configurationInvalid("脱敏策略不能为空");
            }
            strategies.put(type, strategy);
            return this;
        }

        /**
         * 构建不可变策略注册表。
         *
         * @return 不可变策略注册表
         */
        public SensitiveStrategyRegistry build() {
            return new SensitiveStrategyRegistry(strategies);
        }

        /**
         * 注册模块提供的全部内置策略。
         */
        private void registerBuiltIns() {
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
    }
}

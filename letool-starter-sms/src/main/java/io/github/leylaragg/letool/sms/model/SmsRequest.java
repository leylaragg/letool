package io.github.leylaragg.letool.sms.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 描述一次短信发送请求。
 *
 * <p>请求在构建时复制手机号和模板参数，后续修改调用方集合不会影响已经创建的请求。</p>
 */
public final class SmsRequest {

    private final List<String> phones;
    private final String templateCode;
    private final List<SmsParameter> parameters;
    private final String signName;
    private final String provider;

    /**
     * 使用构建器创建不可变请求。
     *
     * @param builder 已完成设置的构建器
     */
    private SmsRequest(Builder builder) {
        this.phones = immutablePhones(builder.phones);
        this.templateCode = requireText(builder.templateCode, "短信模板编码");
        this.parameters = immutableParameters(builder.parameters);
        this.signName = optionalText(builder.signName, "短信签名");
        this.provider = optionalText(builder.provider, "短信 Provider");
    }

    /**
     * 创建短信请求构建器。
     *
     * @return 新构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取目标手机号的不可变列表。
     *
     * @return 目标手机号
     */
    public List<String> getPhones() {
        return phones;
    }

    /**
     * 获取短信模板编码。
     *
     * @return 模板编码
     */
    public String getTemplateCode() {
        return templateCode;
    }

    /**
     * 获取保持插入顺序的模板参数。
     *
     * @return 不可变模板参数列表
     */
    public List<SmsParameter> getParameters() {
        return parameters;
    }

    /**
     * 获取按名称组织的模板参数。
     *
     * @return 不可变有序参数映射
     */
    public Map<String, String> getNamedParameters() {
        Map<String, String> namedParameters = new LinkedHashMap<>();
        for (SmsParameter parameter : parameters) {
            namedParameters.put(parameter.getName(), parameter.getValue());
        }
        return Collections.unmodifiableMap(namedParameters);
    }

    /**
     * 获取按模板占位顺序排列的参数值。
     *
     * @return 不可变参数值列表
     */
    public List<String> getParameterValues() {
        return parameters.stream().map(SmsParameter::getValue).toList();
    }

    /**
     * 获取本次请求覆盖的短信签名。
     *
     * @return 短信签名；未覆盖时为 {@code null}
     */
    public String getSignName() {
        return signName;
    }

    /**
     * 获取本次请求指定的 Provider。
     *
     * @return Provider 名称；使用默认 Provider 时为 {@code null}
     */
    public String getProvider() {
        return provider;
    }

    /**
     * 创建手机号列表的不可变副本并拒绝重复号码。
     *
     * @param source 原始手机号集合
     * @return 不可变手机号列表
     */
    private static List<String> immutablePhones(Collection<String> source) {
        if (source == null || source.isEmpty()) {
            throw new IllegalArgumentException("目标手机号不能为空");
        }
        Set<String> uniquePhones = new LinkedHashSet<>();
        for (String phone : source) {
            String validPhone = requireText(phone, "目标手机号");
            if (!uniquePhones.add(validPhone)) {
                throw new IllegalArgumentException("目标手机号不能重复：" + validPhone);
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(uniquePhones));
    }

    /**
     * 创建模板参数的不可变副本。
     *
     * @param source 原始有序参数映射
     * @return 不可变模板参数列表
     */
    private static List<SmsParameter> immutableParameters(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        List<SmsParameter> result = new ArrayList<>(source.size());
        source.forEach((name, value) -> result.add(SmsParameter.of(name, value)));
        return Collections.unmodifiableList(result);
    }

    /**
     * 校验必填文本。
     *
     * @param value 待校验文本
     * @param fieldName 字段名称
     * @return 原始文本
     */
    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }

    /**
     * 校验可选文本。
     *
     * @param value 待校验文本
     * @param fieldName 字段名称
     * @return 原始文本或 {@code null}
     */
    private static String optionalText(String value, String fieldName) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空字符串");
        }
        return value;
    }

    /**
     * 短信请求构建器。
     */
    public static final class Builder {

        private final List<String> phones = new ArrayList<>();
        private final Map<String, String> parameters = new LinkedHashMap<>();
        private String templateCode;
        private String signName;
        private String provider;

        /**
         * 添加一个目标手机号。
         *
         * @param phone 目标手机号
         * @return 当前构建器
         */
        public Builder phone(String phone) {
            this.phones.add(phone);
            return this;
        }

        /**
         * 添加多个目标手机号。
         *
         * @param phones 目标手机号集合
         * @return 当前构建器
         */
        public Builder phones(Collection<String> phones) {
            if (phones != null) {
                this.phones.addAll(phones);
            }
            return this;
        }

        /**
         * 设置短信模板编码。
         *
         * @param templateCode 模板编码
         * @return 当前构建器
         */
        public Builder templateCode(String templateCode) {
            this.templateCode = templateCode;
            return this;
        }

        /**
         * 添加或覆盖一个模板参数。
         *
         * @param name 参数名称
         * @param value 参数值
         * @return 当前构建器
         */
        public Builder parameter(String name, String value) {
            this.parameters.put(name, value);
            return this;
        }

        /**
         * 批量添加模板参数并保留输入映射的迭代顺序。
         *
         * @param parameters 模板参数
         * @return 当前构建器
         */
        public Builder parameters(Map<String, String> parameters) {
            if (parameters != null) {
                this.parameters.putAll(parameters);
            }
            return this;
        }

        /**
         * 设置本次请求使用的短信签名。
         *
         * @param signName 短信签名
         * @return 当前构建器
         */
        public Builder signName(String signName) {
            this.signName = signName;
            return this;
        }

        /**
         * 设置本次请求使用的 Provider。
         *
         * @param provider Provider 名称
         * @return 当前构建器
         */
        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        /**
         * 构建不可变短信请求。
         *
         * @return 短信请求
         */
        public SmsRequest build() {
            return new SmsRequest(this);
        }
    }
}

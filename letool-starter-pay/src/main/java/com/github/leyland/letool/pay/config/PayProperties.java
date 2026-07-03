package com.github.leyland.letool.pay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付模块配置属性类，对应 YAML 中的 {@code letool.pay} 前缀。
 *
 * <p>该配置类聚合了支付宝、微信支付、银联支付三大渠道的全局配置项。
 * 当前 starter 内置 provider 均为 mock/stub，不会访问真实支付平台；
 * 生产接入应由业务项目注册真实 {@link com.github.leyland.letool.pay.core.PayProvider}。</p>
 *
 * 使用者可在 {@code application.yml} 中按如下结构配置：</p>
 *
 * <pre>{@code
 * letool:
 *   pay:
 *     enabled: true                  # 是否启用支付模块，默认 false
 *     stub-enabled: true             # 是否允许内置 mock/stub provider，默认 false
 *     callback-path: /api/pay/callback  # 回调路径
 *     verify-sign: true              # 是否校验回调签名
 *     alipay:
 *       app-id: 2021001xxxxx
 *       private-key: MIIEvgIBADAN...
 *       alipay-public-key: MIIBIjAN...
 *       sign-type: RSA2
 *     wechat:
 *       app-id: wx1234567890
 *       mch-id: 1234567890
 *       api-v3-key: abcdef123456
 *       cert-serial-no: 1234567890ABCDEF
 *       private-key-path: /path/to/apiclient_key.pem
 *     union:
 *       merchant-id: 7772900581xxxxx
 *       private-key: /path/to/private_key.pfx
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
@ConfigurationProperties(prefix = "letool.pay")
public class PayProperties {

    // ======================== 全局属性 ========================

    /** 是否启用支付模块，默认 false */
    private boolean enabled = false;

    /** 是否允许创建内置 mock/stub provider，默认 false */
    private boolean stubEnabled = false;

    /** 支付平台回调的统一路径前缀，默认 /api/pay/callback */
    private String callbackPath = "/api/pay/callback";

    /** 是否验证支付平台的异步回调签名，默认 true */
    private boolean verifySign = true;

    /** 支付宝配置 */
    private Alipay alipay = new Alipay();

    /** 微信支付配置 */
    private Wechat wechat = new Wechat();

    /** 银联支付配置 */
    private Union union = new Union();

    // ======================== Getter / Setter ========================

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isStubEnabled() { return stubEnabled; }
    public void setStubEnabled(boolean stubEnabled) { this.stubEnabled = stubEnabled; }

    public String getCallbackPath() { return callbackPath; }
    public void setCallbackPath(String callbackPath) { this.callbackPath = callbackPath; }

    public boolean isVerifySign() { return verifySign; }
    public void setVerifySign(boolean verifySign) { this.verifySign = verifySign; }

    public Alipay getAlipay() { return alipay; }
    public void setAlipay(Alipay alipay) { this.alipay = alipay; }

    public Wechat getWechat() { return wechat; }
    public void setWechat(Wechat wechat) { this.wechat = wechat; }

    public Union getUnion() { return union; }
    public void setUnion(Union union) { this.union = union; }

    // ======================== 支付宝配置 ========================

    /**
     * 支付宝支付配置。
     *
     * <p>需在支付宝开放平台创建应用后获取 appId，并配置对应的应用私钥和支付宝公钥。
     * 推荐使用 RSA2（SHA256 with RSA）签名算法。</p>
     */
    public static class Alipay {

        /** 支付宝分配给开发者的应用 ID */
        private String appId;

        /** 应用私钥（PKCS8 格式，去除头尾和换行），用于请求签名 */
        private String privateKey;

        /** 支付宝公钥，用于验证支付宝回调通知的签名 */
        private String alipayPublicKey;

        /** 异步通知地址，支付完成后支付宝会向此地址发送通知 */
        private String notifyUrl;

        /** 签名算法类型，默认 RSA2（建议） */
        private String signType = "RSA2";

        /** 支付宝网关地址，默认 https://openapi.alipay.com/gateway.do */
        private String gatewayUrl = "https://openapi.alipay.com/gateway.do";

        /** 扩展属性 */
        private Map<String, String> extra = new HashMap<>();

        // ---- Getter / Setter ----

        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }

        public String getPrivateKey() { return privateKey; }
        public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }

        public String getAlipayPublicKey() { return alipayPublicKey; }
        public void setAlipayPublicKey(String alipayPublicKey) { this.alipayPublicKey = alipayPublicKey; }

        public String getNotifyUrl() { return notifyUrl; }
        public void setNotifyUrl(String notifyUrl) { this.notifyUrl = notifyUrl; }

        public String getSignType() { return signType; }
        public void setSignType(String signType) { this.signType = signType; }

        public String getGatewayUrl() { return gatewayUrl; }
        public void setGatewayUrl(String gatewayUrl) { this.gatewayUrl = gatewayUrl; }

        public Map<String, String> getExtra() { return extra; }
        public void setExtra(Map<String, String> extra) { this.extra = extra; }
    }

    // ======================== 微信支付配置 ========================

    /**
     * 微信支付配置。
     *
     * <p>支持微信支付 V3 API。需在微信支付商户平台获取商户号、API V3 密钥等配置。
     * 私钥文件为 PEM 格式的商户 API 证书私钥。</p>
     */
    public static class Wechat {

        /** 微信支付分配的公众账号 ID（appId） */
        private String appId;

        /** 微信支付分配的商户号（mchId） */
        private String mchId;

        /** API V3 密钥，用于回调通知的解密和签名 */
        private String apiV3Key;

        /** 商户 API 私钥证书文件路径（PEM 格式） */
        private String privateKeyPath;

        /** 商户 API 证书序列号，用于请求签名 */
        private String certSerialNo;

        /** 异步通知地址，支付完成后微信会向此地址发送通知 */
        private String notifyUrl;

        /** 扩展属性 */
        private Map<String, String> extra = new HashMap<>();

        // ---- Getter / Setter ----

        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }

        public String getMchId() { return mchId; }
        public void setMchId(String mchId) { this.mchId = mchId; }

        public String getApiV3Key() { return apiV3Key; }
        public void setApiV3Key(String apiV3Key) { this.apiV3Key = apiV3Key; }

        public String getPrivateKeyPath() { return privateKeyPath; }
        public void setPrivateKeyPath(String privateKeyPath) { this.privateKeyPath = privateKeyPath; }

        public String getCertSerialNo() { return certSerialNo; }
        public void setCertSerialNo(String certSerialNo) { this.certSerialNo = certSerialNo; }

        public String getNotifyUrl() { return notifyUrl; }
        public void setNotifyUrl(String notifyUrl) { this.notifyUrl = notifyUrl; }

        public Map<String, String> getExtra() { return extra; }
        public void setExtra(Map<String, String> extra) { this.extra = extra; }
    }

    // ======================== 银联支付配置 ========================

    /**
     * 银联支付配置。
     *
     * <p>支持银联在线支付。需在银联商户服务平台注册并获取商户号、配置签名证书。</p>
     */
    public static class Union {

        /** 银联分配的商户号 */
        private String merchantId;

        /** 商户签名私钥路径（PFX 或 PEM 格式） */
        private String privateKey;

        /** 银联公钥路径，用于验证银联回调签名 */
        private String unionPublicKey;

        /** 异步通知地址 */
        private String notifyUrl;

        /** 扩展属性 */
        private Map<String, String> extra = new HashMap<>();

        // ---- Getter / Setter ----

        public String getMerchantId() { return merchantId; }
        public void setMerchantId(String merchantId) { this.merchantId = merchantId; }

        public String getPrivateKey() { return privateKey; }
        public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }

        public String getUnionPublicKey() { return unionPublicKey; }
        public void setUnionPublicKey(String unionPublicKey) { this.unionPublicKey = unionPublicKey; }

        public String getNotifyUrl() { return notifyUrl; }
        public void setNotifyUrl(String notifyUrl) { this.notifyUrl = notifyUrl; }

        public Map<String, String> getExtra() { return extra; }
        public void setExtra(Map<String, String> extra) { this.extra = extra; }
    }
}

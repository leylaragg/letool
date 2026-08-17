package io.github.leylaragg.letool.pay.alipay;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 支付宝 Provider 配置，绑定 {@code letool.pay.alipay}。
 *
 * @author leyland
 * @since 2.0.0
 */
@ConfigurationProperties(prefix = "letool.pay.alipay")
public class AlipayPayProperties {

    private boolean enabled;
    private String appId;
    private String privateKey;
    private String alipayPublicKey;
    private String gatewayUrl = "https://openapi.alipay.com/gateway.do";
    private String format = "json";
    private String charset = "UTF-8";
    private String signType = "RSA2";
    private int connectTimeout = 10_000;
    private int readTimeout = 30_000;

    /** @return Provider 是否启用 */
    public boolean isEnabled() { return enabled; }

    /** @param enabled 是否启用 */
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /** @return 支付宝应用 ID */
    public String getAppId() { return appId; }

    /** @param appId 支付宝应用 ID */
    public void setAppId(String appId) { this.appId = appId; }

    /** @return 应用私钥 */
    public String getPrivateKey() { return privateKey; }

    /** @param privateKey 应用私钥 */
    public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }

    /** @return 支付宝公钥 */
    public String getAlipayPublicKey() { return alipayPublicKey; }

    /** @param alipayPublicKey 支付宝公钥 */
    public void setAlipayPublicKey(String alipayPublicKey) { this.alipayPublicKey = alipayPublicKey; }

    /** @return 支付宝网关地址 */
    public String getGatewayUrl() { return gatewayUrl; }

    /** @param gatewayUrl 支付宝网关地址 */
    public void setGatewayUrl(String gatewayUrl) { this.gatewayUrl = gatewayUrl; }

    /** @return 数据格式 */
    public String getFormat() { return format; }

    /** @param format 数据格式 */
    public void setFormat(String format) { this.format = format; }

    /** @return 字符编码 */
    public String getCharset() { return charset; }

    /** @param charset 字符编码 */
    public void setCharset(String charset) { this.charset = charset; }

    /** @return 签名算法 */
    public String getSignType() { return signType; }

    /** @param signType 签名算法 */
    public void setSignType(String signType) { this.signType = signType; }

    /** @return 连接超时毫秒数 */
    public int getConnectTimeout() { return connectTimeout; }

    /** @param connectTimeout 连接超时毫秒数 */
    public void setConnectTimeout(int connectTimeout) { this.connectTimeout = connectTimeout; }

    /** @return 读取超时毫秒数 */
    public int getReadTimeout() { return readTimeout; }

    /** @param readTimeout 读取超时毫秒数 */
    public void setReadTimeout(int readTimeout) { this.readTimeout = readTimeout; }
}

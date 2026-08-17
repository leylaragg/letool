package io.github.leylaragg.letool.pay.wechat;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 微信支付 V3 Provider 配置，绑定 {@code letool.pay.wechat}。
 *
 * @author leyland
 * @since 2.0.0
 */
@ConfigurationProperties(prefix = "letool.pay.wechat")
public class WechatPayProperties {

    private boolean enabled;
    private String appId;
    private String mchId;
    private String apiV3Key;
    private String merchantSerialNumber;
    private String privateKey;
    private String privateKeyPath;
    private String notifyUrl;
    private String h5Type = "Wap";
    private String h5AppName;
    private String h5AppUrl;
    private String h5BundleId;
    private String h5PackageName;

    /** @return Provider 是否启用 */
    public boolean isEnabled() { return enabled; }

    /** @param enabled 是否启用 */
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /** @return 微信应用 ID */
    public String getAppId() { return appId; }

    /** @param appId 微信应用 ID */
    public void setAppId(String appId) { this.appId = appId; }

    /** @return 微信支付商户号 */
    public String getMchId() { return mchId; }

    /** @param mchId 微信支付商户号 */
    public void setMchId(String mchId) { this.mchId = mchId; }

    /** @return API V3 密钥 */
    public String getApiV3Key() { return apiV3Key; }

    /** @param apiV3Key API V3 密钥 */
    public void setApiV3Key(String apiV3Key) { this.apiV3Key = apiV3Key; }

    /** @return 商户证书序列号 */
    public String getMerchantSerialNumber() { return merchantSerialNumber; }

    /** @param merchantSerialNumber 商户证书序列号 */
    public void setMerchantSerialNumber(String merchantSerialNumber) {
        this.merchantSerialNumber = merchantSerialNumber;
    }

    /** @return 商户私钥正文 */
    public String getPrivateKey() { return privateKey; }

    /** @param privateKey 商户私钥正文 */
    public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }

    /** @return 商户私钥文件路径 */
    public String getPrivateKeyPath() { return privateKeyPath; }

    /** @param privateKeyPath 商户私钥文件路径 */
    public void setPrivateKeyPath(String privateKeyPath) { this.privateKeyPath = privateKeyPath; }

    /** @return 默认异步通知地址 */
    public String getNotifyUrl() { return notifyUrl; }

    /** @param notifyUrl 默认异步通知地址 */
    public void setNotifyUrl(String notifyUrl) { this.notifyUrl = notifyUrl; }

    /** @return H5 场景类型 */
    public String getH5Type() { return h5Type; }

    /** @param h5Type H5 场景类型 */
    public void setH5Type(String h5Type) { this.h5Type = h5Type; }

    /** @return H5 应用名称 */
    public String getH5AppName() { return h5AppName; }

    /** @param h5AppName H5 应用名称 */
    public void setH5AppName(String h5AppName) { this.h5AppName = h5AppName; }

    /** @return H5 网站地址 */
    public String getH5AppUrl() { return h5AppUrl; }

    /** @param h5AppUrl H5 网站地址 */
    public void setH5AppUrl(String h5AppUrl) { this.h5AppUrl = h5AppUrl; }

    /** @return iOS 应用 Bundle ID */
    public String getH5BundleId() { return h5BundleId; }

    /** @param h5BundleId iOS 应用 Bundle ID */
    public void setH5BundleId(String h5BundleId) { this.h5BundleId = h5BundleId; }

    /** @return Android 应用包名 */
    public String getH5PackageName() { return h5PackageName; }

    /** @param h5PackageName Android 应用包名 */
    public void setH5PackageName(String h5PackageName) { this.h5PackageName = h5PackageName; }
}

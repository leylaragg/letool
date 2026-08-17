# letool-starter-pay-wechat

基于微信支付官方 Java SDK V3 的 Letool 支付 Provider，支持 Native、H5、APP、JSAPI、查询、关单、退款、退款查询以及通知验签解密。

## 引用

```xml
<dependency>
    <groupId>io.github.leylaragg</groupId>
    <artifactId>letool-starter-pay-wechat</artifactId>
    <version>${letool.version}</version>
</dependency>
```

该依赖会传递引入 `letool-starter-pay`，无需重复声明核心模块。

## 配置

```yaml
letool:
  pay:
    enabled: true
    default-provider: wechat
    wechat:
      enabled: true
      app-id: ${WECHAT_APP_ID}
      mch-id: ${WECHAT_MCH_ID}
      api-v3-key: ${WECHAT_API_V3_KEY}
      merchant-serial-number: ${WECHAT_MERCHANT_SERIAL}
      private-key-path: /secure/apiclient_key.pem
      notify-url: https://example.com/pay/wechat/notify
      h5-type: Wap
      h5-app-name: Example
      h5-app-url: https://example.com
```

`private-key` 和 `private-key-path` 至少配置一个；两者同时存在时优先使用私钥正文。官方 `RSAAutoCertificateConfig` 负责请求签名、平台证书轮换、响应验签和通知解密。

场景映射：`QR_CODE` 对应 Native，`WAP` 对应 H5，`APP` 对应 APP 支付，`JSAPI` 对应公众号/小程序支付。H5 请求必须提供 `clientIp`；`Wap` 类型配置 `h5-app-url`，`iOS` 类型配置 `h5-bundle-id`，`Android` 类型配置 `h5-package-name`。JSAPI 请求必须通过 `payerId` 提供用户 `openid`。微信退款还必须提供原订单 `totalAmount`。

## 回调

Controller 必须保留未改写的原始 JSON 正文，并传入 `Wechatpay-Serial`、`Wechatpay-Timestamp`、`Wechatpay-Nonce`、`Wechatpay-Signature` 请求头。Provider 将这些材料交给官方 `NotificationParser` 验签并解密；验证失败会抛出 `PAY_007`，不存在关闭验签的开关。

业务方负责数据库事务、金额复核、通知幂等和向微信返回正确应答。框架不会自动创建 Controller、支付表或重试退款请求。

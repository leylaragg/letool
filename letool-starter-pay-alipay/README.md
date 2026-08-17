# letool-starter-pay-alipay

基于支付宝官方 Java SDK 的 Letool 支付 Provider，支持电脑网站、手机网站、APP、当面付预创建、查询、关单、退款、退款查询和 RSA 回调验签。

## 引用

```xml
<dependency>
    <groupId>io.github.leylaragg</groupId>
    <artifactId>letool-starter-pay-alipay</artifactId>
    <version>${letool.version}</version>
</dependency>
```

该依赖会传递引入 `letool-starter-pay`，无需重复声明核心模块。

## 配置

```yaml
letool:
  pay:
    enabled: true
    default-provider: alipay
    alipay:
      enabled: true
      app-id: ${ALIPAY_APP_ID}
      private-key: ${ALIPAY_PRIVATE_KEY}
      alipay-public-key: ${ALIPAY_PUBLIC_KEY}
      gateway-url: https://openapi.alipay.com/gateway.do
      charset: UTF-8
      sign-type: RSA2
      connect-timeout: 10000
      read-timeout: 30000
```

请求中的 `notifyUrl`、`returnUrl` 按订单传入。`PAGE`、`WAP` 返回 `formHtml`，`APP` 返回官方 SDK 生成的签名订单字符串，`QR_CODE` 返回二维码地址。支付宝不支持公共契约中的 `JSAPI` 场景。

## 回调

支付宝通知使用表单参数验签。Controller 应将收到的每个原始表单字段放入 `PayNotificationRequest.formParameter`，然后调用 `payTemplate.parseNotification`。Provider 使用配置的支付宝公钥和官方 `AlipaySignature` 强制验签；验签失败会抛出 `PAY_007`，不存在跳过验签的配置。

业务方仍负责数据库事务、金额复核、通知幂等和向支付宝返回 `success`。同步跳转地址不能作为支付成功依据。

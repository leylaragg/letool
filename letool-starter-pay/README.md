# letool-starter-pay

支付公共契约与统一便捷入口。核心模块提供不可变请求/响应模型、Provider 路由、统一异常和显式 Mock；真实支付宝、微信支付能力分别由独立 Provider 模块提供。

## 如何选择依赖

只使用一个真实支付平台时，仅引用对应模块即可，它会传递引入支付核心：

```xml
<!-- 支付宝 -->
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-pay-alipay</artifactId>
    <version>${letool.version}</version>
</dependency>

<!-- 微信支付（二选一或同时引用） -->
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-pay-wechat</artifactId>
    <version>${letool.version}</version>
</dependency>
```

仅使用自定义 Provider 或开发 Mock 时，直接引用 `letool-starter-pay`。

## 核心配置

```yaml
letool:
  pay:
    enabled: true
    default-provider: alipay # 请求没有指定 provider 时使用
    mock:
      enabled: false         # 只用于开发和自动化测试
```

支付模块默认关闭；启用后没有任何 `PayProvider` 会启动失败。Provider 名称忽略大小写，重复名称会启动失败。回调验签不存在关闭开关。

## 统一调用

```java
PayRequest request = PayRequest.builder()
        .provider("alipay")
        .scene(PayScene.PAGE)
        .outTradeNo("ORDER-20260805-001")
        .subject("会员订单")
        .amount(new BigDecimal("99.00"))
        .notifyUrl("https://example.com/pay/alipay/notify")
        .returnUrl("https://example.com/pay/result")
        .build();

PayResponse response = payTemplate.create(request);
PayAction action = response.getAction();
```

`PayAction` 告诉调用端下一步如何处理：

| 类型 | 典型参数 | 用途 |
|---|---|---|
| `FORM_HTML` | `formHtml` | 支付宝电脑/手机网站表单 |
| `REDIRECT_URL` | `redirectUrl` | 微信 H5 跳转地址 |
| `QR_CODE_URL` | `codeUrl` | 支付宝或微信 Native 二维码 |
| `APP_ORDER_STRING` | 平台签名参数 | 移动端 APP SDK 调起 |
| `JSAPI_PARAMETERS` | `appId`、`paySign` 等 | 微信 JSAPI 调起 |

查询、关单和退款使用独立请求模型：

```java
PayResponse queried = payTemplate.query(PayQueryRequest.builder()
        .provider("alipay")
        .outTradeNo("ORDER-20260805-001")
        .build());

RefundResponse refunded = payTemplate.refund(RefundRequest.builder()
        .provider("wechat")
        .outTradeNo("ORDER-20260805-001")
        .outRefundNo("REFUND-20260805-001")
        .amount(new BigDecimal("10.00"))
        .totalAmount(new BigDecimal("99.00")) // 微信退款必填
        .reason("用户申请退款")
        .build());
```

当前公共契约仅接受人民币，金额必须大于零且最多两位有效小数。支付状态和退款状态分别由 `PayStatus`、`RefundStatus` 表达；平台响应不确定时返回 `UNKNOWN` 或抛出 `PAY_006`，调用方应主动查询，框架不会自动重试资金操作。

## 回调边界

Letool 不自动创建回调 Controller，也不替业务项目维护支付表。业务方应保留原始请求，调用 `parseNotification` 完成官方验签/解密，再在本地数据库事务中处理幂等：

```java
PayNotificationRequest request = PayNotificationRequest.builder()
        .provider("wechat")
        .rawBody(rawBody)
        .header("Wechatpay-Serial", serial)
        .header("Wechatpay-Timestamp", timestamp)
        .header("Wechatpay-Nonce", nonce)
        .header("Wechatpay-Signature", signature)
        .build();

PayNotification notification = payTemplate.parseNotification(request);
payCallbackHandler.handle(notification);
```

`PayCallbackHandler` 是有意保留给用户实现的业务扩展接口。推荐在实现中以平台通知 ID 或平台交易号建立唯一约束，锁定本地订单，校验金额和币种，幂等更新订单并记录原始业务审计信息。只有本地事务成功后才向平台返回成功应答。

## 自定义 Provider

接入其他平台时实现 `PayProvider` 并注册为 Spring Bean。实现必须使用平台可靠的签名能力，并实现创建、查询、关单、退款、退款查询和通知解析全部契约；不能以“固定成功”代替尚未实现的资金操作。

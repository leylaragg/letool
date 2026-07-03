# letool-starter-pay

> 支付抽象模块，保留支付宝/微信支付/Mock 支付的统一 API 入口，支持下单、回调、退款和查询模型。

> ⚠️ 当前支付宝、微信支付 provider 为 Stub 实现，Mock provider 仅用于开发测试。支付 starter 默认不启用；如需开发演示必须显式设置 `letool.pay.stub-enabled=true`。下单、查询、退款、验签和回调处理均为模拟行为，不能用于真实资金链路，生产接入请注册真实 `PayProvider`。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-pay</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 快速开始（开发 Stub 模式）

### 1. 添加依赖并配置

```yaml
letool:
  pay:
    enabled: true
    stub-enabled: true
    callback-path: /api/pay/callback
    verify-sign: true
    alipay:
      app-id: 2021001xxxxx
      private-key: MIIEvgIBADAN...
      alipay-public-key: MIIBIjAN...
      sign-type: RSA2
    wechat:
      app-id: wx1234567890
      mch-id: 1234567890
      api-v3-key: abcdef123456
      cert-serial-no: 1234567890ABCDEF
      private-key-path: /path/to/apiclient_key.pem
```

### 2. 发起支付

```java
@Autowired
private PayTemplate payTemplate;

// 构建支付订单
PayOrder order = PayOrder.builder()
        .channel(PayChannel.WECHAT)
        .outTradeNo("ORD-" + System.currentTimeMillis())
        .subject("测试商品")
        .totalAmount(new BigDecimal("0.01"))
        .build();

// 发起支付
PayResult result = payTemplate.pay(order);
```

### 3. 处理回调

```java
@Component
public class OrderPayCallbackHandler implements PayCallbackHandler {

    @Override
    public PayResult handleCallback(PayChannel channel, Map<String, String> params) {
        // 内置 Stub 仅做模拟验签；生产环境必须使用真实 PayProvider
        String outTradeNo = params.get("out_trade_no");
        updateOrderStatus(outTradeNo, "PAID");
        return PayResult.success(outTradeNo);
    }
}
```

## 配置属性

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `letool.pay.enabled` | boolean | false | 是否启用支付模块 |
| `letool.pay.stub-enabled` | boolean | false | 是否允许创建内置 Mock/Stub provider；生产环境必须关闭并注册真实 PayProvider |
| `letool.pay.callback-path` | String | /api/pay/callback | 回调路径前缀 |
| `letool.pay.verify-sign` | boolean | true | 是否校验回调签名；内置 Stub provider 的验签是模拟行为 |
| `letool.pay.alipay.app-id` | String | - | 支付宝应用 ID |
| `letool.pay.alipay.private-key` | String | - | 应用私钥（PKCS8） |
| `letool.pay.alipay.alipay-public-key` | String | - | 支付宝公钥 |
| `letool.pay.alipay.sign-type` | String | RSA2 | 签名算法 |
| `letool.pay.alipay.gateway-url` | String | openapi.alipay.com | 支付宝网关 |
| `letool.pay.wechat.app-id` | String | - | 微信支付 appId |
| `letool.pay.wechat.mch-id` | String | - | 微信支付商户号 |
| `letool.pay.wechat.api-v3-key` | String | - | API V3 密钥 |
| `letool.pay.wechat.private-key-path` | String | - | 商户私钥证书路径 |
| `letool.pay.wechat.cert-serial-no` | String | - | 证书序列号 |
| `letool.pay.union.merchant-id` | String | - | 银联商户号 |

## 核心 API

### 编程式——统一支付操作（PayTemplate）

```java
@Autowired
private PayTemplate payTemplate;

// 发起支付
PayOrder order = PayOrder.builder()
        .channel(PayChannel.ALIPAY)
        .outTradeNo("ORD-20240101-001")
        .subject("MacBook Pro")
        .totalAmount(new BigDecimal("14999.00"))
        .notifyUrl("https://example.com/api/pay/callback/alipay")
        .build();
PayResult result = payTemplate.pay(order);

// 查询订单
PayResult queryResult = payTemplate.query("ORD-20240101-001", PayChannel.ALIPAY);

// 发起退款
RefundOrder refund = RefundOrder.builder()
        .channel(PayChannel.WECHAT)
        .outTradeNo("ORD-20240101-001")
        .outRefundNo("REF-20240102-001")
        .refundAmount(new BigDecimal("0.01"))
        .reason("用户申请退款")
        .build();
PayResult refundResult = payTemplate.refund(refund);

// 处理回调
Map<String, String> callbackParams = request.getParameterMap();
PayResult callbackResult = payTemplate.handleCallback(PayChannel.WECHAT, callbackParams);
```

### 注解声明式——实现 PayProvider 接入新渠道

```java
@Component
public class AlipayProvider implements PayProvider {

    @Override
    public PayResult pay(PayOrder order) {
        // 构建支付宝统一下单请求并返回支付 URL
    }

    @Override
    public PayResult query(String outTradeNo) {
        // 向支付宝查询订单状态
    }

    @Override
    public PayResult refund(RefundOrder refundOrder) {
        // 发起支付宝退款
    }

    @Override
    public PayResult queryRefund(String refundNo) {
        // 查询退款状态
    }

    @Override
    public boolean verifySign(Map<String, String> params, String sign) {
        // RSA2 签名验证
    }

    @Override
    public String getProviderName() {
        return "ALIPAY";
    }
}
```

### 注解声明式——回调处理器

```java
@Component
public class PaymentCallbackProcessor implements PayCallbackHandler {

    @Override
    public PayResult handleCallback(PayChannel channel, Map<String, String> params) {
        String outTradeNo = params.get("out_trade_no");
        String tradeNo = params.get("trade_no");

        // 更新订单状态
        orderService.markPaid(outTradeNo, tradeNo);

        // 发送通知
        notificationService.notifyUser(outTradeNo);

        return PayResult.success(outTradeNo);
    }
}
```

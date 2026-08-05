# letool-starter-sms-tencent

基于腾讯云 SMS 3.0 产品级官方 SDK 的 Letool Provider，不引入腾讯云全产品 SDK。

## 依赖

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-sms-tencent</artifactId>
</dependency>
```

## 配置

```yaml
letool:
  sms:
    enabled: true
    tencent:
      secret-id: ${TENCENTCLOUD_SECRET_ID}
      secret-key: ${TENCENTCLOUD_SECRET_KEY}
      sdk-app-id: 1400000000
      sign-name: 已审核短信签名
      region: ap-guangzhou
      endpoint: sms.tencentcloudapi.com
      default-country-code: "86"
      connect-timeout-seconds: 10
      read-timeout-seconds: 10
      write-timeout-seconds: 10
```

腾讯云要求 E.164 手机号。请求已经包含 `+` 和国家码时会原样校验；纯数字手机号会使用 `default-country-code` 补全。

腾讯云模板参数只有顺序没有名称。使用 Builder 连续调用 `param` 时，Letool 会按添加顺序映射参数值。

业务注册 `SmsClient` Bean 时可以接管动态凭证、代理和客户端配置。注册名为 `tencentSmsProvider` 的 Bean 时，客户端和默认 Provider 都会退让。

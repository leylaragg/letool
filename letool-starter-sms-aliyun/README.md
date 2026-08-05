# letool-starter-sms-aliyun

基于阿里云短信 V2 官方 SDK 的 Letool Provider。引入本模块后会自动传递引入短信核心模块。

## 依赖

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-sms-aliyun</artifactId>
</dependency>
```

## 配置

推荐通过阿里云默认凭证链提供凭证：

```yaml
letool:
  sms:
    enabled: true
    aliyun:
      sign-name: 已审核短信签名
      region-id: cn-hangzhou
      endpoint: dysmsapi.aliyuncs.com
```

也可以配置静态密钥或 STS 临时凭证：

```yaml
letool:
  sms:
    aliyun:
      access-key-id: ${ALIBABA_CLOUD_ACCESS_KEY_ID}
      access-key-secret: ${ALIBABA_CLOUD_ACCESS_KEY_SECRET}
      security-token: ${ALIBABA_CLOUD_SECURITY_TOKEN:}
```

业务注册 `com.aliyun.dysmsapi20170525.Client` Bean 时，Letool 不再创建客户端。注册名为 `aliyunSmsProvider` 的 Bean 时，客户端和默认 Provider 都会退让。

请求中的 `signName` 可以覆盖默认签名。模板参数按名称序列化为阿里云要求的 JSON 对象。

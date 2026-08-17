# letool-starter-oss-tencent-cos

使用腾讯云 COS 官方 Java SDK 实现 Letool `OssProvider`，支持上传、下载、幂等删除、存在性检查和 GET 预签名地址。

## 引入依赖

```xml
<dependency>
    <groupId>io.github.leylaragg</groupId>
    <artifactId>letool-starter-oss-tencent-cos</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 最小配置

```yaml
letool:
  oss:
    enabled: true
    provider: tencent-cos
    bucket: assets-1250000000
    tencent-cos:
      region: ap-guangzhou
      secret-id: ${TENCENT_SECRET_ID}
      secret-key: ${TENCENT_SECRET_KEY}
      session-token: ${TENCENT_SESSION_TOKEN:} # 临时凭证时配置
```

| 属性 | 必填 | 说明 |
|---|---|---|
| `letool.oss.tencent-cos.region` | 是 | COS 地域，例如 `ap-guangzhou`；提供 `ClientConfig` 或客户端 Bean 时可省略 |
| `letool.oss.tencent-cos.secret-id` | 是 | SecretId；提供凭证 Bean 或客户端 Bean 时可省略 |
| `letool.oss.tencent-cos.secret-key` | 是 | SecretKey；提供凭证 Bean 或客户端 Bean 时可省略 |
| `letool.oss.tencent-cos.session-token` | 否 | 临时会话令牌 |

默认 Bucket 必须包含腾讯云 APPID 后缀。

## 自定义客户端

业务可以按需注册以下 Bean：

- `com.qcloud.cos.auth.COSCredentialsProvider`：接入腾讯云原生动态凭证刷新策略。
- `com.qcloud.cos.auth.COSCredentials`：替换 Letool 配置的长期或临时静态凭证。
- `com.qcloud.cos.ClientConfig`：配置地域、连接池、超时、代理和重试参数。
- `com.qcloud.cos.COS`：完整接管官方客户端创建和生命周期。

Letool 检测到用户 Bean 后会退让，但仍可提供统一的 `TencentCosProvider` 和 `OssTemplate`。公共 API、流关闭规则和自定义 `OssProvider` 方法见 [`letool-starter-oss`](../letool-starter-oss/README.md)。

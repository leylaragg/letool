# letool-starter-oss-aliyun

使用阿里云 OSS 官方 Java SDK 实现 Letool `OssProvider`，支持上传、下载、幂等删除、存在性检查和 GET 预签名地址。

## 引入依赖

```xml
<dependency>
    <groupId>io.github.leylaragg</groupId>
    <artifactId>letool-starter-oss-aliyun</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 最小配置

```yaml
letool:
  oss:
    enabled: true
    provider: aliyun
    bucket: assets
    aliyun:
      endpoint: https://oss-cn-hangzhou.aliyuncs.com
      access-key-id: ${ALIYUN_ACCESS_KEY_ID}
      access-key-secret: ${ALIYUN_ACCESS_KEY_SECRET}
      security-token: ${ALIYUN_SECURITY_TOKEN:} # 临时凭证时配置
```

| 属性 | 必填 | 说明 |
|---|---|---|
| `letool.oss.aliyun.endpoint` | 是 | OSS 服务 Endpoint |
| `letool.oss.aliyun.access-key-id` | 是 | AccessKeyId；提供凭证 Bean 或客户端 Bean 时可省略 |
| `letool.oss.aliyun.access-key-secret` | 是 | AccessKeySecret；提供凭证 Bean 或客户端 Bean 时可省略 |
| `letool.oss.aliyun.security-token` | 否 | STS 临时安全令牌 |

## 自定义客户端

业务可以按需注册以下 Bean：

- `com.aliyun.oss.common.auth.CredentialsProvider`：接入动态凭证或自定义刷新策略。
- `com.aliyun.oss.ClientBuilderConfiguration`：配置连接池、超时、代理和重试参数。
- `com.aliyun.oss.OSS`：完整接管官方客户端创建和生命周期。

Letool 检测到用户 Bean 后会退让，但仍可提供统一的 `AliyunOssProvider` 和 `OssTemplate`。公共 API、流关闭规则和自定义 `OssProvider` 方法见 [`letool-starter-oss`](../letool-starter-oss/README.md)。

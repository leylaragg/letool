# letool-starter-oss-minio

使用 MinIO 官方 Java SDK 实现 Letool `OssProvider`，支持上传、下载、幂等删除、存在性检查和 GET 预签名地址。

## 引入依赖

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-oss-minio</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 最小配置

```yaml
letool:
  oss:
    enabled: true
    provider: minio
    bucket: assets
    minio:
      endpoint: http://localhost:9000
      access-key: minioadmin
      secret-key: minioadmin
      region: us-east-1 # 可选
```

| 属性 | 必填 | 说明 |
|---|---|---|
| `letool.oss.minio.endpoint` | 是 | MinIO 服务地址 |
| `letool.oss.minio.access-key` | 是 | 静态访问密钥；提供凭证 Bean 或客户端 Bean 时可省略 |
| `letool.oss.minio.secret-key` | 是 | 静态私密密钥；提供凭证 Bean 或客户端 Bean 时可省略 |
| `letool.oss.minio.region` | 否 | 服务地域 |

未知长度流会使用 MinIO SDK 的分片上传能力，不会伪造 `Content-Length`。

## 自定义客户端

业务可以注册 `io.minio.MinioClient` Bean 完整接管客户端配置，或注册 `io.minio.credentials.Provider` Bean 实现动态凭证。Letool 检测到用户 Bean 后会退让，但仍提供统一的 `MinioOssProvider` 和 `OssTemplate`。

公共 API、流关闭规则和自定义 `OssProvider` 方法见 [`letool-starter-oss`](../letool-starter-oss/README.md)。

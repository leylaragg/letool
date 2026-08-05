# letool-starter-oss

对象存储统一门面。核心模块负责公共契约、默认 Bucket、参数校验、输入类型适配和统一异常；真实网络访问由独立 Provider 模块通过官方 SDK 完成。

## 模块选择

业务项目通常只需要引入一个 Provider starter，它会传递引入核心模块：

| 存储服务 | Maven 模块 | Provider 标识 |
|---|---|---|
| MinIO 或兼容 S3 的 MinIO 服务 | `letool-starter-oss-minio` | `minio` |
| 阿里云 OSS | `letool-starter-oss-aliyun` | `aliyun` |
| 腾讯云 COS | `letool-starter-oss-tencent-cos` | `tencent-cos` |

仅在实现自定义 `OssProvider` 时，才需要单独引入 `letool-starter-oss`。

## 快速开始

以下以 MinIO 为例。

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-oss-minio</artifactId>
    <version>${letool.version}</version>
</dependency>
```

### 2. 配置 Provider

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
```

`letool.oss.enabled` 默认为 `false`。启用后必须存在一个可用的 `OssProvider`；核心模块不会创建模拟实现或伪造成功结果。

### 3. 使用统一门面

```java
@Autowired
private OssTemplate ossTemplate;

// 上传字节数组
OssUploadResult result = ossTemplate.upload(
        "images/avatar.png",
        imageBytes,
        "image/png");

// 上传本地文件，文件流由模板负责关闭
OssUploadResult fileResult = ossTemplate.upload(
        "reports/annual.pdf",
        Path.of("annual.pdf"));

// 下载流必须由调用方关闭
try (OssObject object = ossTemplate.download("reports/annual.pdf")) {
    object.getContent().transferTo(outputStream);
}

boolean exists = ossTemplate.exists("reports/annual.pdf");
URI signedUrl = ossTemplate.getPresignedUrl(
        "reports/annual.pdf",
        Duration.ofMinutes(15));

// 删除按对象存储服务的幂等语义执行，无返回值
ossTemplate.delete("reports/annual.pdf");
```

## 高级上传

需要指定 Bucket、准确长度或用户元数据时，使用不可变请求模型：

```java
OssUploadRequest request = OssUploadRequest.builder()
        .bucket("archive")
        .objectKey("2026/report.pdf")
        .inputStream(inputStream)
        .contentLength(contentLength)
        .contentType("application/pdf")
        .metadata(Map.of("tenant", "tenant-a"))
        .build();

OssUploadResult result = ossTemplate.upload(request);
```

输入流的生命周期规则：

- `InputStream` 和 `OssUploadRequest` 上传由调用方关闭输入流。
- `Path` 上传由 `OssTemplate` 创建并关闭文件流。
- 下载返回 `OssObject`，调用方必须使用 try-with-resources 关闭。

## 公共配置

| 属性 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `letool.oss.enabled` | `boolean` | `false` | 是否启用 OSS 自动配置 |
| `letool.oss.provider` | `String` | `minio` | 选择 `minio`、`aliyun` 或 `tencent-cos` |
| `letool.oss.bucket` | `String` | 无 | 快捷方法使用的默认 Bucket |

厂商凭证、Endpoint、地域和自定义客户端配置见对应 Provider 模块 README。

## 扩展与覆盖

Provider starter 均遵循 Spring Boot 退让规则：

- 注册官方 SDK 客户端 Bean，可完整接管客户端创建、代理、超时、重试和凭证刷新。
- 注册厂商凭证或客户端配置 Bean，可保留 Letool Provider，只替换对应底层能力。
- 注册自定义 `OssProvider` Bean，可接入其他对象存储服务；Letool 仍会创建统一 `OssTemplate`。
- 注册自定义 `OssTemplate` Bean 时，公共自动配置会退让。

所有官方 SDK 异常都会保留原因链并转换为稳定的 `OSS_*` 错误码；Letool 不吞掉网络、鉴权或服务端失败。

## 破坏性变更

本轮生产化移除了原有 Stub Provider 及以下配置：

- `letool.oss.stub-enabled`
- `letool.oss.default-provider`
- 厂商配置下的独立 `bucket`

统一使用 `letool.oss.provider` 选择 Provider，使用 `letool.oss.bucket` 配置默认 Bucket。`upload` 现在返回 `OssUploadResult`，`download` 返回可关闭的 `OssObject`，预签名地址返回 `URI`，`delete` 调整为无返回值的幂等操作。

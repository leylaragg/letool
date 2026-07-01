# letool-starter-oss

> 对象存储模块，阿里云 OSS / 腾讯云 COS / MinIO / AWS S3 统一 API，支持 STS 临时凭证、URL 签名。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-oss</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 快速开始（3 分钟上手）

### 1. 添加依赖并配置

```yaml
letool:
  oss:
    enabled: true
    default-provider: minio
    minio:
      endpoint: http://localhost:9000
      access-key: minioadmin
      secret-key: minioadmin
      bucket: my-bucket
```

### 2. 上传文件

```java
@Autowired
private OssTemplate ossTemplate;

// 快捷上传（使用默认 Bucket）
String url = ossTemplate.upload("photos/avatar.png", inputStream);
```

### 3. 下载与预签名 URL

```java
// 下载文件
InputStream is = ossTemplate.download("photos/avatar.png");

// 获取预签名 URL（1 小时有效）
String signedUrl = ossTemplate.getPresignedUrl("photos/avatar.png", Duration.ofHours(1));

// 删除文件
ossTemplate.delete("photos/avatar.png");
```

## 配置属性

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `letool.oss.enabled` | boolean | true | 是否启用 OSS 模块 |
| `letool.oss.default-provider` | String | minio | 默认提供商：aliyun / minio / tencent-cos |
| `letool.oss.aliyun.endpoint` | String | - | 阿里云 OSS Endpoint |
| `letool.oss.aliyun.access-key-id` | String | - | AccessKeyId |
| `letool.oss.aliyun.access-key-secret` | String | - | AccessKeySecret |
| `letool.oss.aliyun.bucket` | String | - | 默认 Bucket |
| `letool.oss.minio.endpoint` | String | - | MinIO 服务地址 |
| `letool.oss.minio.access-key` | String | - | Access Key |
| `letool.oss.minio.secret-key` | String | - | Secret Key |
| `letool.oss.minio.bucket` | String | - | 默认 Bucket |
| `letool.oss.tencent-cos.secret-id` | String | - | 腾讯云 SecretId |
| `letool.oss.tencent-cos.secret-key` | String | - | 腾讯云 SecretKey |
| `letool.oss.tencent-cos.region` | String | - | COS 地域（如 ap-guangzhou） |
| `letool.oss.tencent-cos.bucket` | String | - | 默认 Bucket（含 APPID 后缀） |

## 核心 API

### 编程式——OssTemplate 快捷操作（使用默认 Bucket）

```java
@Autowired
private OssTemplate ossTemplate;

// 上传（默认 Content-Type）
String url = ossTemplate.upload("photos/avatar.png", inputStream);

// 上传（指定 Content-Type）
String url = ossTemplate.upload("documents/report.pdf", inputStream, "application/pdf");

// 下载
InputStream is = ossTemplate.download("photos/avatar.png");

// 删除
boolean deleted = ossTemplate.delete("photos/avatar.png");

// 获取预签名 URL
String signedUrl = ossTemplate.getPresignedUrl("photos/avatar.png", Duration.ofHours(2));

// 检查对象是否存在
boolean exists = ossTemplate.exists("photos/avatar.png");
```

### 编程式——指定 Bucket 操作

```java
// 上传到指定 Bucket
String url = ossTemplate.upload("archive-bucket", "2024/report.pdf",
        inputStream, "application/pdf");

// 从指定 Bucket 下载
InputStream is = ossTemplate.download("archive-bucket", "2024/report.pdf");

// 删除指定 Bucket 中的文件
boolean deleted = ossTemplate.delete("archive-bucket", "2024/report.pdf");

// 指定 Bucket 的预签名 URL
String signedUrl = ossTemplate.getPresignedUrl("archive-bucket",
        "2024/report.pdf", Duration.ofMinutes(30));
```

### 编程式——Builder 模式链式调用

```java
// 上传
String url = ossTemplate.builder()
        .bucket("archive-bucket")
        .objectKey("2024/report.pdf")
        .contentType("application/pdf")
        .upload(inputStream);

// 下载
InputStream is = ossTemplate.builder()
        .objectKey("photos/avatar.png")
        .download();

// 删除
boolean deleted = ossTemplate.builder()
        .bucket("my-bucket")
        .objectKey("temp/log.txt")
        .delete();

// 获取预签名 URL
String signedUrl = ossTemplate.builder()
        .objectKey("private/secret.pdf")
        .getPresignedUrl(Duration.ofMinutes(15));

// 检查存在性
boolean exists = ossTemplate.builder()
        .objectKey("photos/avatar.png")
        .exists();
```

### 注解声明式——通过配置切换多提供商

```yaml
letool:
  oss:
    default-provider: aliyun   # 切换到阿里云 OSS，无需修改代码
    aliyun:
      endpoint: oss-cn-hangzhou.aliyuncs.com
      access-key-id: your-access-key-id
      access-key-secret: your-access-key-secret
      bucket: prod-bucket
```

模块根据 `default-provider` 自动创建对应的 `OssProvider` 实现，业务代码中注入的 `OssTemplate` 无需任何改动即可切换存储后端。如需同时使用多个提供商，可通过 Builder 模式在每次操作时手动指定 `bucket` 参数来路由到不同配置。

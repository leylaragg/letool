# letool-starter-file

面向业务开发的文件操作便利 Starter。模块提供统一的 `FileTemplate`、流式存储扩展接口、
Local/FTP/FTPS 内置实现、上传安全限制、下载响应适配和 ZIP 安全压缩解压能力。

本模块不会自动暴露 REST 接口，也不包含 SFTP、MinIO 或云 OSS 的伪实现。对象存储请使用
`letool-starter-oss` 及对应官方 SDK Provider；其他存储协议可以通过
`FileStorageProvider` 接入。

## 引入依赖

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-file</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 本地存储快速开始

本地存储是默认实现，只需要指定一个业务可写目录：

```yaml
letool:
  file:
    enabled: true
    upload:
      max-size: 20MB
      allow-empty: false
      allowed-extensions: [jpg, png, pdf]
      allowed-content-types: [image/jpeg, image/png, application/pdf]
    storage:
      type: local
      local:
        base-path: /data/application/files
```

业务代码主要注入 `FileTemplate`：

```java
@RestController
@RequestMapping("/files")
public class FileController {

    private final FileTemplate fileTemplate;

    public FileController(FileTemplate fileTemplate) {
        this.fileTemplate = fileTemplate;
    }

    @PostMapping
    public StoredFile upload(@RequestParam MultipartFile file) {
        return fileTemplate.upload(file, "orders/2026");
    }

    @GetMapping("/{key}")
    public void download(
            @PathVariable String key,
            HttpServletResponse response) {
        FileMetadata metadata = fileTemplate.stat(key);
        fileTemplate.download(key, metadata.name(), response);
    }
}
```

`StoredFile.key()` 是后续下载、查询和删除使用的逻辑键，不会泄露本地绝对路径。
下载过程中模块不会关闭 Servlet 输出流；FTP/FTPS 下载返回的远程资源连接会随
`FileResource.close()` 一起释放。

常用非 Web API：

```java
try (FileResource resource = fileTemplate.open(key)) {
    InputStream inputStream = resource.inputStream();
    FileMetadata metadata = resource.metadata();
    // 在当前作用域内消费输入流
}

boolean exists = fileTemplate.exists(key);
FileMetadata metadata = fileTemplate.stat(key);
List<FileMetadata> children = fileTemplate.list("orders/2026");
boolean deleted = fileTemplate.delete(key);
```

## FTP 与 FTPS

FTP 和 FTPS 共用连接配置。生产环境优先选择 `ftps`；选择明文 `ftp` 时模块会输出安全警告。

```yaml
letool:
  file:
    storage:
      type: ftps
      ftp:
        host: files.example.com
        port: 21
        username: ${FILE_FTP_USERNAME}
        password: ${FILE_FTP_PASSWORD}
        base-path: /application
        charset: UTF-8
        passive-mode: true
        connect-timeout: 10s
        default-timeout: 10s
        data-timeout: 30s
        keep-alive-interval: 30s
        protocol: TLS
        implicit: false
        endpoint-checking-enabled: true
```

内置实现使用远程临时文件上传，写入完成后再重命名；下载使用 Commons Net 流式接口，
关闭资源时会执行 `completePendingCommand()` 并释放连接。账号密码不要写入代码或提交到仓库。

## 上传校验扩展

扩展名和媒体类型白名单只属于基础校验。模块会读取有限文件头进行轻量类型探测，
但不会把压缩容器简单宣称为 DOCX。业务可以注册有序 `FileValidationPolicy` Bean：

```java
@Component
@Order(100)
public class PdfUploadPolicy implements FileValidationPolicy {

    @Override
    public void validate(FileValidationContext context) {
        if ("pdf".equals(context.extension())
                && !"PDF".equals(context.detectedType())) {
            throw FileException.of(FileErrorCode.UPLOAD_REJECTED, "PDF 文件头不匹配");
        }
    }
}
```

需要病毒扫描、完整内容识别或隔离区审核时，应在业务中实现专门策略或自定义存储流程；
有限文件头校验不能替代完整安全扫描。

## ZIP 压缩与安全解压

`FileTemplate` 会应用 `letool.file.archive` 配置，限制条目数量、单条目实际大小和实际总量：

```yaml
letool:
  file:
    archive:
      max-entries: 10000
      max-entry-size: 100MB
      max-total-size: 1GB
```

```java
fileTemplate.compress(sourcePath, outputZip, true);
fileTemplate.decompress(inputZip, targetDirectory);
```

解压会在写入前预检 ZIP 中央目录，并拒绝绝对路径、目录穿越、重复条目、符号链接和超限内容；
写入过程中还会按实际字节数再次计数，不能依赖可伪造的 ZIP 声明大小。

## 自定义存储 Provider

业务只需提供一个 `FileStorageProvider` Bean，默认 Local/FTP/FTPS Provider 会自动退让：

```java
@Bean
public FileStorageProvider businessFileStorage() {
    return new BusinessFileStorageProvider();
}
```

Provider 只处理逻辑键、输入流和元数据，不依赖 Multipart、Servlet 或具体 Controller。
实现 `open` 时必须把远程客户端生命周期绑定到返回的 `FileResource`；实现类还应准确返回
`capabilities()`，不能声明尚未实现的断点读取等能力。

## 关键配置

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `letool.file.enabled` | `true` | 文件模块开关 |
| `letool.file.upload.max-size` | `10MB` | 声明大小和实际读取大小的共同上限 |
| `letool.file.upload.allow-empty` | `false` | 是否允许空文件 |
| `letool.file.upload.allowed-extensions` | 空 | 扩展名白名单 |
| `letool.file.upload.allowed-content-types` | 空 | 声明媒体类型白名单 |
| `letool.file.storage.type` | `local` | `local`、`ftp` 或 `ftps` |
| `letool.file.storage.local.base-path` | `${user.home}/letool/files` | 本地存储根目录 |
| `letool.file.archive.max-entries` | `10000` | ZIP 最大条目数 |
| `letool.file.archive.max-entry-size` | `100MB` | ZIP 单条目最大实际解压大小 |
| `letool.file.archive.max-total-size` | `1GB` | ZIP 最大实际解压总量 |

## 破坏性变更迁移

本次生产化调整不保留旧伪契约：

| 旧用法 | 新用法 |
| --- | --- |
| `FileUploadService.upload(...)` | `FileTemplate.upload(...)` |
| `FileDownloadService.download(...)` | `FileTemplate.download(...)` 或 `open(...)` |
| `FileStorageProvider.upload/download` | `store/open` 流式契约 |
| `FileStorageProvider.FileInfo` | `FileMetadata` |
| `upload.allowed-types` | `upload.allowed-extensions` |
| `upload.storage-path` | `storage.local.base-path` |
| `storage.type=sftp/minio/oss` | 自定义 `FileStorageProvider`，对象存储优先使用 `letool-starter-oss` |

统一异常类型为 `FileException`，稳定错误码范围为 `FILE_001` 至 `FILE_010`。

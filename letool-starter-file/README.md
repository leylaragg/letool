# letool-starter-file

## 模块简介

文件操作模块，提供统一的文件上传、下载、删除和列表查询能力。支持多存储后端（本地文件系统 / FTP / SFTP / MinIO / OSS），通过配置即可切换。内置基于文件魔术数字（Magic Number）的类型检测，比扩展名校验更可靠。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-file</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 快速开始（3 分钟上手）

**Step 1** — 配置存储后端：

```yaml
letool:
  file:
    enabled: true
    upload:
      max-size: 10MB
      allowed-types: [jpg, png, pdf, docx]
    storage:
      type: local           # local / ftp / sftp / minio / oss
      local:
        base-path: /var/files
```

**Step 2** — 注入 FileStorageProvider 使用：

```java
@Autowired
private FileStorageProvider storageProvider;

// 上传
String filePath = storageProvider.upload(
        inputStream,
        "2026/07",          // 路径（相对于存储根目录）
        "report.pdf"        // 文件名
);

// 下载
InputStream is = storageProvider.download(filePath);

// 删除
storageProvider.delete(filePath);

// 检查是否存在
boolean exists = storageProvider.exists(filePath);

// 列出目录内容
List<FileStorageProvider.FileInfo> files = storageProvider.list("2026/07");
```

**Step 3** — 文件类型魔数检测：

```java
String type = FileTypeUtil.detect(inputStream);
System.out.println(type);           // PNG / JPEG / PDF / ZIP / UNKNOWN

boolean isImg = FileTypeUtil.isImage(inputStream);
boolean isZip = FileTypeUtil.isArchive(inputStream);
```

## 配置属性

```yaml
letool:
  file:
    enabled: true                    # 模块开关，默认 true
    upload:
      max-size: 10MB                 # 单文件最大体积，默认 10MB
      allowed-types: [jpg, pdf]      # 扩展名白名单，空则不限制
      storage-path: /data/uploads    # 上传存储路径，默认 /data/uploads
    storage:
      type: local                    # 存储类型：local / ftp / sftp，默认 local
      local:
        base-path: ~/letool/files    # 本地存储根目录
      ftp:
        host: localhost              # FTP 地址
        port: 21                     # FTP 端口
        username: admin
        password: secret
        passive-mode: true           # 被动模式，默认 true
        connect-timeout: 10000       # 连接超时(ms)，默认 10000
      sftp:
        host: localhost              # SFTP 地址
        port: 22                     # SFTP 端口
        username: root
        password: ""                 # 与 private-key-path 二选一
        private-key-path: ""
        connect-timeout: 10000
```

## 核心 API 示例

### 1. 文件上传与下载

```java
@Autowired
private FileStorageProvider storageProvider;

// 上传文件
public String uploadFile(MultipartFile file) {
    try (InputStream is = file.getInputStream()) {
        return storageProvider.upload(is, "documents/2026", file.getOriginalFilename());
    } catch (IOException e) {
        throw new RuntimeException("上传失败", e);
    }
}

// 下载文件
public InputStream downloadFile(String path) {
    return storageProvider.download(path);
}
```

### 2. 魔术数字类型检测

基于文件头部字节序列（Magic Number）判断真实类型，防止用户通过改扩展名绕过限制：

```java
public void validateFileType(InputStream is) {
    String type = FileTypeUtil.detect(is);
    if ("UNKNOWN".equals(type)) {
        throw new RuntimeException("无法识别的文件类型");
    }
}

// 快捷判断
boolean isImage = FileTypeUtil.isImage(is);      // PNG/JPEG/GIF/BMP
boolean isArchive = FileTypeUtil.isArchive(is);   // ZIP/RAR/GZIP
```

支持的检测类型包括：PNG、JPEG、GIF、BMP、PDF、DOC、XML、ZIP（含 DOCX）、RAR、GZIP、MP3、MPEG、Java CLASS 等 15 种格式。

### 3. FileStorageProvider 接口

所有存储实现都遵循统一契约，上层服务依赖接口而非具体实现，通过配置切换后端：

```java
public interface FileStorageProvider {
    String upload(InputStream inputStream, String path, String fileName);
    InputStream download(String path);
    boolean delete(String path);
    boolean exists(String path);
    List<FileInfo> list(String path);
}
```

`FileInfo` 为不可变对象，包含 name、path、size、directory、lastModified 五个字段。

### 4. 切換存储后端

只需修改配置，无需改动任何业务代码：

```yaml
# 切换为 FTP 存储
letool.file.storage.type: ftp
letool.file.storage.ftp.host: 192.168.1.100

# 切换为 SFTP 存储
letool.file.storage.type: sftp
letool.file.storage.sftp.private-key-path: /keys/id_rsa
```

### 5. 文件列表查询

```java
List<FileStorageProvider.FileInfo> files = storageProvider.list("documents/2026");
for (FileStorageProvider.FileInfo f : files) {
    System.out.printf("%s | %d bytes | %s%n",
            f.getName(), f.getSize(),
            f.isDirectory() ? "[目录]" : "[文件]");
}
```

# letool-starter-mail

## 模块简介

邮件模块，基于 Jakarta Mail 封装，提供 Builder 模式的链式调用 API。支持纯文本/HTML 邮件、模板变量、文件附件、多 SMTP 账户配置、同步/异步双模式发送。内置线程池管理，异步发送不阻塞调用方。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-mail</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 快速开始（3 分钟上手）

**Step 1** — 配置 SMTP 账户：

```yaml
letool:
  mail:
    enabled: true
    default-account: primary
    async: true
    async-pool-size: 4
    accounts:
      primary:
        host: smtp.example.com
        port: 587
        username: noreply@example.com
        password: yourpassword
        protocol: smtp
        auth: true
        starttls: true
        from: noreply@example.com
        personal: "系统通知"
```

**Step 2** — 注入 MailTemplate 发送邮件：

```java
@Autowired
private MailTemplate mailTemplate;

// 同步发送纯文本
mailTemplate.builder()
    .to("user@example.com")
    .subject("密码重置通知")
    .text("您的密码已成功重置。")
    .send();

// 异步发送 HTML 邮件
mailTemplate.builder()
    .to("user@example.com")
    .cc("admin@example.com")
    .subject("本周资讯")
    .html("<h1>本周要闻</h1><p>欢迎阅读。</p>")
    .sendAsync()
    .thenAccept(resp -> log.info("发送结果: {}", resp.isSuccess()));
```

## 配置属性

```yaml
letool:
  mail:
    enabled: true                # 模块开关，默认 true
    default-account: primary     # 默认账户名，对应 accounts 下的 key
    async: false                 # 是否异步发送，默认 false（同步）
    async-pool-size: 4           # 异步线程池大小，默认 4
    accounts:
      <account-name>:
        host: localhost          # SMTP 服务器地址
        port: 25                 # SMTP 端口，默认 25
        username: ""             # 登录用户名
        password: ""             # 登录密码
        protocol: smtp           # 协议：smtp / smtps，默认 smtp
        auth: true               # 是否启用认证，默认 true
        starttls: false          # 是否启用 STARTTLS，默认 false
        ssl: false               # 是否启用 SSL，默认 false
        from: ""                 # 发件人地址
        personal: ""             # 发件人显示名称
```

## 核心 API 示例

### 1. 编程式：MailTemplate Builder 链式调用

MailTemplate 是用户操作邮件模块的核心入口，通过 `builder()` 方法创建 `MailRequestBuilder`，支持流畅的链式调用：

**同步发送：**

```java
MailResponse response = mailTemplate.builder()
    .from("support@example.com", "技术支持")
    .to("customer@example.com")
    .cc("manager@example.com")
    .bcc("audit@example.com")
    .subject("工单处理通知")
    .text("您的工单 #1234 已被受理。")
    .send();

if (response.isSuccess()) {
    log.info("邮件发送成功, messageId={}", response.getMessageId());
}
```

**异步发送：**

```java
// 单条异步
mailTemplate.builder()
    .to("user@example.com")
    .subject("通知")
    .html("<h1>系统通知</h1>")
    .sendAsync()
    .thenAccept(resp -> log.info("发送结果: {}", resp.isSuccess()));

// 群发异步
List<String> recipients = List.of("a@example.com", "b@example.com");
for (String recipient : recipients) {
    mailTemplate.builder()
        .to(recipient)
        .subject("批量通知")
        .text("这是一条批量通知")
        .sendAsync();
}
```

### 2. 编程式：带附件和模板变量

```java
mailTemplate.builder()
    .to("report@example.com")
    .subject("月度报告")
    .template("monthly-report")          // 模板名称
    .variable("userName", "张三")         // 注入变量到模板
    .variable("reportDate", "2026-06")
    .attachment("report.pdf", new File("/path/to/report.pdf"))  // 文件附件
    .send();
```

### 3. 多账户配置

支持为不同业务场景配置独立 SMTP 账户，通过 `default-account` 切换当前使用的账户：

```yaml
letool:
  mail:
    default-account: marketing
    accounts:
      system:
        host: smtp.system.com
        from: system@company.com
        personal: "系统通知"
      marketing:
        host: smtp.marketing.com
        from: marketing@company.com
        personal: "市场部"
```

### 4. 直接发送已构建的 MailRequest

```java
MailRequest request = new MailRequest();
request.addTo("user@example.com");
request.setSubject("手动构建");
request.setContent("内容");
request.setHtml(false);

// 同步
MailResponse resp = mailTemplate.send(request);

// 异步
CompletableFuture<MailResponse> future = mailTemplate.sendAsync(request);
```

`sendAsync()` 内部通过 `CompletableFuture.supplyAsync()` 在独立线程池中执行发送，异常封装在 CompletableFuture 中，调用方需自行处理。

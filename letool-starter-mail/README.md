# letool-starter-mail

## 模块定位

`letool-starter-mail` 是基于 Jakarta Mail 的轻量发送适配层，负责：

- 纯文本、HTML 和附件邮件的 MIME 构造；
- TO、CC、BCC 收件人与请求级 SMTP 账户选择；
- 同步发送和受控线程池异步发送；
- SMTP 配置校验、有限超时和统一异常；
- 通过 `MailSender` 接口替换默认发送实现。

本模块不再内置邮件模板渲染。业务可使用任意模板引擎生成 HTML，再调用
`MailTemplate.html(...)` 发送，避免邮件模块绑定 Thymeleaf、Freemarker 等具体方案。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-mail</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## SMTP 配置

默认发送器只有在 `letool.mail.enabled=true` 时创建。启用后必须配置有效账户，
否则应用会在启动阶段失败；不会再静默回退到本机 `localhost:25`。

```yaml
letool:
  mail:
    enabled: true
    default-account: primary
    async-pool-size: 4
    async-queue-capacity: 1000
    accounts:
      primary:
        host: smtp.example.com
        port: 587
        username: noreply@example.com
        password: ${SMTP_PASSWORD}
        protocol: smtp
        auth: true
        starttls: true
        ssl: false
        from: noreply@example.com
        personal: "系统通知"
        connection-timeout-millis: 10000
        read-timeout-millis: 10000
        write-timeout-millis: 10000
      marketing:
        host: smtp.marketing.example.com
        port: 465
        username: marketing@example.com
        password: ${MARKETING_SMTP_PASSWORD}
        protocol: smtps
        auth: true
        starttls: false
        ssl: true
        from: marketing@example.com
        personal: "市场团队"
        connection-timeout-millis: 10000
        read-timeout-millis: 10000
        write-timeout-millis: 10000
```

配置约束：

- `protocol` 仅支持 `smtp` 和 `smtps`；
- `port` 必须在 `1` 到 `65535` 之间；
- `starttls` 和 `ssl` 不能同时开启；
- `auth=true` 时必须同时提供用户名和密码；
- 三项超时必须为正数，默认均为 `10000` 毫秒；
- `async-pool-size` 和 `async-queue-capacity` 必须为正数；
- 默认发送地址必须是合法邮箱；
- 密码应从环境变量或密钥管理系统注入，不要写入仓库。

## 基本用法

### 同步发送

```java
MailResponse response = mailTemplate.builder()
        .to("user@example.com")
        .subject("密码重置通知")
        .text("您的密码已成功重置。")
        .send();
```

### HTML、抄送与附件

```java
MailResponse response = mailTemplate.builder()
        .from("support@example.com", "技术支持")
        .to("customer@example.com")
        .cc("manager@example.com")
        .bcc("audit@example.com")
        .subject("月度报告")
        .html("<h1>月度报告</h1><p>请查收附件。</p>")
        .attachment("report.pdf", new File("/path/to/report.pdf"))
        .send();
```

### 请求级账户选择

不调用 `account(...)` 时使用 `default-account`；指定的账户必须存在且配置有效。

```java
mailTemplate.builder()
        .account("marketing")
        .to("subscriber@example.com")
        .subject("本周资讯")
        .html("<h1>本周资讯</h1>")
        .send();
```

### 异步发送

```java
mailTemplate.builder()
        .to("user@example.com")
        .subject("异步通知")
        .text("任务已经完成。")
        .sendAsync()
        .thenAccept(response -> log.info("发送结果：{}", response.isSuccess()))
        .exceptionally(error -> {
            log.error("邮件发送失败", error);
            return null;
        });
```

`sendAsync()` 会在提交任务前完成校验并创建不可变请求快照。因此，调用方后续修改原始
`MailRequest` 不会改变已经提交的邮件。异步任务使用有界队列，队列满时以 `MAIL_004`
拒绝新任务，避免突发流量无限占用内存。附件文件本身必须保持可读，直到异步任务完成。

Spring 管理的 `MailTemplate` 会随容器关闭线程池。脱离 Spring 单独创建时，应使用
try-with-resources 或显式调用 `close()`，已提交任务会继续完成，关闭后不再接受新任务。

## 直接发送 MailRequest

```java
MailRequest request = new MailRequest();
request.setAccountName("primary");
request.addTo("user@example.com");
request.setSubject("手动构建");
request.setContent("邮件内容");
request.setHtml(false);

MailResponse response = mailTemplate.send(request);
CompletableFuture<MailResponse> future = mailTemplate.sendAsync(request);
```

发送前至少需要一名 TO、CC 或 BCC 收件人，并提供非空主题和非 `null` 内容。邮箱地址、
附件名称和附件可读性会在请求快照阶段校验。传给 `MailSender` 的对象是不可修改的快照。

## 自定义发送器

`MailSender` 是保留给用户的真实扩展接口，不是待删除的占位实现。注册自定义 Bean 后，
自动配置会让默认 Jakarta Mail 发送器退让；此时无需配置 SMTP 账户。

```java
@Bean
MailSender internalGatewayMailSender(InternalMailGateway gateway) {
    return request -> {
        String messageId = gateway.deliver(request);
        return MailResponse.success(messageId);
    };
}
```

自定义实现可以接入企业邮件网关、审计系统或测试替身。实现必须返回非 `null`
`MailResponse`；抛出的原始运行时异常会由 `MailTemplate` 收敛为统一邮件异常。

## 异常约定

邮件异常统一为 `MailException`，可通过 `getCode()` 获取稳定错误码。

| 错误码 | 含义 |
| --- | --- |
| `MAIL_001` | 邮件配置不合法 |
| `MAIL_002` | 邮件请求不合法 |
| `MAIL_003` | 邮件投递失败 |
| `MAIL_004` | 异步执行器不可用 |

对外错误消息不会包含 SMTP 主机、账号、密码、收件人、主题或底层供应商异常文本；
详细原因保留在异常 `cause` 中供受控日志和诊断使用。

## 2.0 迁移说明

- **删除伪模板 API：** `MailTemplate.MailRequestBuilder.template(...)`、
  `variable(...)`、`MailRequest.templateName` 和 `MailRequest.variables`
  过去只保存数据而从未渲染。请先在业务层完成模板渲染，再调用 `html(...)`。
- **删除无效配置：** `letool.mail.async` 不再使用。调用 `send()` 或
  `sendAsync()` 明确选择执行方式，`async-pool-size` 仍用于异步线程池。
- **默认配置改为快速失败：** 启用默认发送器时必须提供存在且合法的 SMTP 账户；
  自定义 `MailSender` 不受该限制。
- **请求校验更严格：** 非法邮箱、空主题、缺少收件人和不可读附件会在投递前失败；
  异步发送使用不可变请求快照。
- **异常构造方式收敛：** 不再支持任意文本的 `MailException` 公共构造器。
  调用方应捕获 `MailException` 并根据稳定错误码处理。

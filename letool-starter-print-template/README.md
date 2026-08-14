# letool-starter-print-template

`letool-starter-print-template` 管理动态打印模板的集合版本和发布过程。模块只依赖打印核心，不绑定 XML、Spring、数据库、文件系统或具体业务。

## 模板集合

一个集合由发布方分配正整数版本，并至少包含一个 `DOCUMENT`：

- `DOCUMENT`：可直接发起打印的完整文档。
- `FRAGMENT`：供完整文档或其他片段引用的复用内容。

```java
long version = 2026081401L;
PrintTemplate main = new PrintTemplate(
        "patient-report",
        TemplateFormat.LETOOL_XML,
        1,
        version,
        1,
        xmlBytes);

TemplateDefinition definition = new TemplateDefinition(
        TemplateType.DOCUMENT,
        main);
```

发布时会检查模板代码、集合版本、数量和正文总字节数，并按模板代码生成稳定的 SHA-256 摘要。摘要使用 JDK 能力实现，避免为了单个散列功能引入较重的工具依赖链。

## 发布与激活

```java
TemplateRepository repository = new InMemoryTemplateRepository();
TemplateSetPublisher publisher = new TemplateSetPublisher(
        repository,
        List.of(candidate -> validateBusinessContract(candidate)));

TemplateSet published = publisher.publishAndActivate(
        version,
        List.of(definition));
```

- `publish`：保存新版本，不改变当前版本。
- `publishAndActivate`：在一次原子状态替换中保存并激活新版本。
- `activate`：切换到已经发布的版本。

同一版本不能覆盖，旧版本在切换后仍可按版本读取。版本由宿主或发布系统生成，框架不会替业务决定版本规则。

## 校验扩展

`TemplateSetValidator` 在集合进入仓库前执行，适合接入格式编译、引用图和业务无关的契约校验。校验器由可信 Java 代码注册，应支持并发调用，并且不能依赖修改候选集合。

XML 模块通过 `XmlTemplateSetValidator` 接入 `<include>` 和引用图校验，从而保持模板模块到 XML 模块的单向扩展关系：

```java
XmlTemplateCompiler xmlCompiler = new XmlTemplateCompiler();
XmlTemplateSetCompiler setCompiler = new XmlTemplateSetCompiler(xmlCompiler);

TemplateSetPublisher publisher = new TemplateSetPublisher(
        repository,
        List.of(new XmlTemplateSetValidator(setCompiler)));
```

校验器会在仓库写入前完成全部 XML 定义解析、引用目标与版本检查、循环检测和容量治理。其他格式模板可以与 XML 文档共存，但不能作为 XML `include` 目标。XML 模块还允许发布校验与运行时共享同一份有界编译缓存，避免合法集合在首次打印时重复编译。

## 编译键

`TemplateCompilationKey` 描述一次可复用编译的完整条件：集合版本与摘要、模板代码、DSL 版本、上下文版本、渲染器配置版本和输出格式。模板模块只提供这个通用值对象，不依赖 XML 或具体缓存实现。

```java
TemplateCompilationKey key = new TemplateCompilationKey(
        templateSet.version(),
        templateSet.digest(),
        template.templateCode(),
        template.dslVersion(),
        template.contextVersion(),
        rendererProfileVersion,
        OutputFormat.PDF);
```

集合摘要参与键比较，因此即使宿主错误地提供了相同版本号，不同内容也不会共享编译结果。渲染器配置或输出格式变化同样会形成新键。XML、JasperReports 和后续其它编译器可以复用这个身份契约，但各自维护自己的编译产物缓存。

## 内存仓库边界

`InMemoryTemplateRepository` 是线程安全的参考实现，使用不可变状态和 CAS 保证发布、激活与读取的一致性。它不提供删除、容量淘汰、自动清理或持久化；生产系统可按 `TemplateRepository` 契约实现数据库或远程仓库。

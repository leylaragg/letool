# letool-starter-print-expression-spel

`letool-starter-print-expression-spel` 是动态打印框架的可选受限 SpEL 条件表达式模块。它只提供条件读取与比较能力，不提供完整 SpEL。

## 引入模块

```xml
<dependency>
    <groupId>io.github.leylaragg</groupId>
    <artifactId>letool-starter-print-expression-spel</artifactId>
    <version>${letool.version}</version>
</dependency>
```

模块只直接依赖 `letool-starter-print-xml` 和 `spring-expression`，不依赖 Spring Context、Spring Boot、`letool-starter-tool`、规则引擎或业务项目。

## 显式注册

依赖进入 classpath 不会自动启用 SpEL。宿主必须使用可信 Java 代码显式注册：

```java
PrintExpressionRegistry expressions = new PrintExpressionRegistry(List.of(
        new RestrictedSpelConditionExpression()));

XmlTemplateCompiler compiler = new XmlTemplateCompiler(
        BuiltInPrintFormatters.registry(),
        expressions,
        new PrintTagRegistry(List.of()));
```

未引入模块或没有注册提供方时，包含 `expression-language="spel"` 的模板会在编译阶段失败，不会自动发现、自动降级或改变默认结构化条件。

## XML 示例

```xml
<document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
    <page>
        <page-body>
            <if expression-language="spel"
                test="agreement.status == 'ACTIVE' &amp;&amp; agreement.amount &gt;= 100">
                <then><paragraph>条件成立</paragraph></then>
            </if>
        </page-body>
    </page>
</document>
```

`expression-language/test` 必须同时出现，并且不能与结构化条件的 `path/operator/value/value-type` 混用。默认的结构化条件始终可用，不需要本模块。

## 支持语法

第一版只支持：

- 根 JSON 属性读取，例如 `document.status`；
- 当前循环变量读取，例如 `item.enabled`；
- JSON 数组非负整数字面量下标，例如 `items[0].status`；
- 字符串、整数、小数、布尔和 `null` 字面量；
- 括号；
- 逻辑非 `!`、逻辑与 `&&`、逻辑或 `||`；
- `==`、`!=`、`<`、`<=`、`>`、`>=`。

当前循环变量与根属性同名时，循环变量优先。嵌套循环继续遵循 XML 编译器既有契约，不允许声明与外层重名的循环变量。最终结果必须是真实 `Boolean`；字符串、数字、对象、数组和 `null` 不会转换成真值。

SpEL 中的循环变量直接写成 `item.enabled`。`$item.enabled` 是 XML 数据路径的写法，在 SpEL 中会被拒绝，不能混用两套语法。

编译计划会从通过白名单校验的 AST 中提取实际属性读取链，交给 `TemplateInspection` 供宿主预检。循环变量和片段参数会规范为 `$item.path` 形式，与 XML 字段路径保持一致；根路径仍保持 `document.path`。检查结果保留数组下标和首次出现顺序，但不保存表达式正文、字面量或 AST。

显式 JSON `null` 可以参与 `== null` 判断。属性缺失、对标量继续取属性、对象使用数组下标或数组越界均安全失败，不会降级为 `null`。

## 禁止语法

编译器按 Spring AST 节点白名单默认拒绝，至少禁止：

- Bean、变量、`#root`、`#this`；
- `T(...)`、构造器、方法和自定义函数；
- 反射、类加载和 `class/getClass/classLoader/declaringClass` 等元数据属性；
- 属性或数组写入、赋值、自增、自减；
- 加减乘除、取模、幂和字符串拼接；
- 三元、Elvis、安全导航、正则、`instanceof`；
- 集合或 Map 构造、范围、筛选、投影、聚合；
- `#{...}` 模板表达式、脚本及其他未明确允许的 AST 节点。

Spring 后续新增的 AST 类型不会自动放行。增加语法必须修改明确白名单并补充安全测试。

## 数据与异常边界

- 每次求值只读取 `PrintDataView` 提供的标准 JSON 防御性副本；
- 内部包装对象不会暴露 `JsonNode`、业务 POJO、可变集合或 Spring 容器；
- 只安装专用只读属性和数组访问器，不安装反射属性访问器、方法解析器或 Bean 解析器；
- 编译和求值复用打印框架现有异常、错误码与国际化链路；
- 用户可见错误不回显表达式正文、业务值、AST、Java 类路径或 Spring 实现消息；
- 编译计划可以并发复用，每次求值的数据根、词法变量、上下文和预算相互隔离。

## 资源治理

固定安全上限包括：

- 表达式正文最多 4096 个字符；
- Spring 解析前的圆括号、方括号和花括号累计嵌套深度最多 32；
- 解析前连续逻辑非最多 31 个、条件运算符最多 32 个；
- AST 最多 128 个节点、最大深度 32；
- 单条连续读取链最多 32 个属性或下标节点；
- 整棵表达式最多 16 个数组下标；
- 单个字符串字面量最多 1024 个解码后字符；
- 单个数字字面量最多 64 个源码字符；
- 单次求值最多 256 个 checkpoint；
- 单次求值采用 250 毫秒合作式软截止检查。

软截止时间不是抢占式线程中断。AST 白名单先保证操作有界，属性读取和求值边界再通过 checkpoint 尽早失败。以上限制不是业务配置项，不能通过公共构造器放宽；XML 绑定还会继续执行累计动态操作、生成节点和文本总量治理。

## Letool 工具复用

本模块复用打印框架已有异常、国际化、表达式 SPI、`PrintDataView` 和 XML 中央限制。现有 `SpelUtil` 面向可信表达式，没有本模块所需的 AST 白名单与打印预算，因此不直接复用。

后续需要 JSON、IO 或其他能力时，应先检查 Letool 现有工具的语义和完整依赖树。工具缺少能力时可以补充公共模块，但新增 API、命名、异常和测试必须脱离打印场景仍然成立；打印专用能力保留在本模块。基础工具不得反向依赖打印模块，也不得形成 Maven 循环依赖。

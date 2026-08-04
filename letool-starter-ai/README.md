# letool-starter-ai

`letool-starter-ai` 是面向业务开发的 Spring AI 1.1.8 Provider 中立薄封装。模块适配
Spring Boot 3.5.x，当前工程统一管理 Spring Boot 3.5.16。

Letool 只负责以下便利能力：

- 按 Spring Bean 名称路由 `ChatModel` 与 `EmbeddingModel`；
- 单模型自动选择和多模型默认项校验；
- 按 `ChatModel` 缓存原生 `ChatClient`；
- 通过有序 `AiChatClientCustomizer` 统一定制客户端；
- 将 Letool 自身的配置、路由和定制失败转换为结构化 `AiException`。

Provider、HTTP/SSE、模型重试、Tools、Advisor、RAG 与 `VectorStore` 都由 Spring AI
及用户选择的 Starter 负责。本模块不会重复实现这些能力，也不会替用户创建具体模型或
`SimpleVectorStore`。

## 依赖与版本

先引入 Letool AI Starter：

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-ai</artifactId>
    <version>${letool.version}</version>
</dependency>
```

再显式选择一个 Spring AI Provider Starter。以下仅以 OpenAI 为例，并不代表 Letool
绑定 OpenAI：

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
```

若项目已按 [`docs/bom-usage.md`](../docs/bom-usage.md) 导入 Letool 依赖管理 POM，
Spring AI 依赖版本由其中的 Spring AI BOM 1.1.8 管理，无需在 Provider Starter 上重复
写版本；否则请在应用的 `dependencyManagement` 中显式导入 `spring-ai-bom:1.1.8`。
更换 Provider 时，只需改为对应的 Spring AI Starter，并按该 Provider 官方文档配置。

## Provider 配置示例

模型鉴权、端点与模型参数使用所选 Spring AI Provider 的原生配置。OpenAI 示例：

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o-mini
          temperature: 0.2
      embedding:
        options:
          model: text-embedding-3-small
```

`letool.ai.*` 不接收 API Key、Base URL、超时、重试或模型参数。Provider 鉴权、
端点与模型参数使用 `spring.ai.<provider>.*`；Spring AI 1.1.8 通用重试使用
`spring.ai.retry.*`；连接/读取超时、代理及底层客户端参数是否可配置、使用何种属性，
应以所选 Provider 的官方文档为准。

## Letool 配置

| 属性 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `letool.ai.enabled` | Boolean | `true` | 是否启用模型注册表与调用门面 |
| `letool.ai.default-chat-model` | String | - | 多个 `ChatModel` 候选时必填，值为 Bean 名称 |
| `letool.ai.default-embedding-model` | String | - | 多个 `EmbeddingModel` 候选时必填，值为 Bean 名称 |

某类模型只有一个 Bean 且未配置默认项时，Letool 会自动选中它；有多个 Bean 且没有
默认项时会在启动阶段以 `AI_CONFIGURATION_INVALID` 拒绝歧义。配置值会去除首尾空白，
但按 Bean 名称查询时使用精确匹配。

## 单模型调用

Provider Starter 创建唯一 `ChatModel` 后，可以直接使用默认客户端：

```java
@Service
public class AssistantService {

    private final AiTemplate aiTemplate;

    public AssistantService(AiTemplate aiTemplate) {
        this.aiTemplate = aiTemplate;
    }

    public String answer(String question) {
        return aiTemplate.chatClient()
                .prompt()
                .system("你是严谨的 Java 助手")
                .user(question)
                .call()
                .content();
    }
}
```

需要绕过 `ChatClient` 时，可通过 `aiTemplate.chatModel()` 与
`aiTemplate.embeddingModel()` 获取 Spring AI 原生模型 Bean。

## 多模型路由

应用可以按所选 Provider 的官方方式声明多个 `ChatModel` Bean，并为每个 Bean 使用
稳定、唯一的名称。Letool 不推测 Provider 名或远端模型名，只认 Spring Bean 名称：

```yaml
letool:
  ai:
    default-chat-model: customerChatModel
    default-embedding-model: knowledgeEmbeddingModel
```

```java
String defaultAnswer = aiTemplate.chatClient()
        .prompt("介绍一下 Letool")
        .call()
        .content();

String auditAnswer = aiTemplate.chatClient("auditChatModel")
        .prompt("检查这段文本中的合规风险")
        .call()
        .content();

Set<String> availableModels = aiTemplate.chatModelNames();
ChatModel rawModel = aiTemplate.chatModel("auditChatModel");
```

`AiModelRegistry` 在构造时复制并排序模型映射，后续新增或删除模型 Bean 不会动态改变
已经建立的注册表。

## Spring AI 原生能力

以下 API 均来自 Spring AI 1.1.8，Letool 不再维护平行实现。

### 同步与流式调用

```java
String content = aiTemplate.chatClient()
        .prompt("用三句话解释虚拟线程")
        .call()
        .content();

Flux<String> chunks = aiTemplate.chatClient()
        .prompt("逐步说明一次代码评审")
        .stream()
        .content();
```

流式调用返回 Reactor `Flux`；应用还需满足所选 Provider 的流式运行时依赖和网络配置。

### 结构化输出

```java
public record ReleaseNote(String title, List<String> changes) {
}

ReleaseNote note = aiTemplate.chatClient()
        .prompt("将本次改动整理为结构化发布说明")
        .call()
        .entity(ReleaseNote.class);
```

结构化输出的准确性取决于模型和 Provider 对格式约束的支持，生产代码仍应校验返回对象。

### `@Tool` 工具调用

```java
public final class OrderTools {

    @Tool(name = "query_order", description = "根据订单号查询订单状态")
    public String queryOrder(String orderNo) {
        return "订单 " + orderNo + " 已发货";
    }
}

String result = aiTemplate.chatClient()
        .prompt("查询订单 A100 的状态")
        .tools(new OrderTools())
        .call()
        .content();
```

工具描述、参数转换、执行循环和 Provider 能力由 Spring AI 负责。工具实现属于业务代码，
必须自行处理鉴权、幂等、超时和敏感数据。

### Advisor 与 RAG

RAG 需要应用额外引入 Spring AI 1.1.8 的 Advisor 模块和选定的向量库 Starter：

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-advisors-vector-store</artifactId>
</dependency>
<!-- 再按 Spring AI 文档选择一个生产级 VectorStore Starter -->
```

应用自行声明并初始化 `VectorStore` 后，再把原生 Advisor 交给客户端：

```java
@Bean
AiChatClientCustomizer ragCustomizer(VectorStore vectorStore) {
    QuestionAnswerAdvisor advisor = QuestionAnswerAdvisor.builder(vectorStore).build();
    return (modelName, builder) -> {
        if ("knowledgeChatModel".equals(modelName)) {
            builder.defaultAdvisors(advisor);
        }
    };
}
```

也可以逐次调用：

```java
String answer = aiTemplate.chatClient("knowledgeChatModel")
        .prompt("公司的退款规则是什么？")
        .advisors(QuestionAnswerAdvisor.builder(vectorStore).build())
        .call()
        .content();
```

本 Starter 不会自动创建 `SimpleVectorStore`。该实现适合演示或测试，不应被无提示地当作
生产向量库；生产应用应显式选择、配置和观测持久化 `VectorStore`。

## 统一定制 ChatClient

`AiChatClientCustomizer` 会按 Spring `Ordered` 规则排序，并对每个 ChatModel 的独立
`ChatClient.Builder` 执行一次：

```java
@Component
public final class CommonClientCustomizer
        implements AiChatClientCustomizer, Ordered {

    @Override
    public void customize(String modelName, ChatClient.Builder builder) {
        builder.defaultSystem("回答必须标明不确定信息");
    }

    @Override
    public int getOrder() {
        return 100;
    }
}
```

定制器运行失败时抛出 `AI_CLIENT_CUSTOMIZATION_FAILED`，并保留原始原因链。对同一模型
重复调用 `chatClient(...)` 会返回已缓存的客户端，而不是重复执行定制器。

## 自动配置与替换边界

启用时，自动配置只创建两个 Bean：

- `AiModelRegistry`：收集现有 `ChatModel` / `EmbeddingModel`，并在用户已提供同类型 Bean
  时退让；
- `AiTemplate`：基于注册表和有序 Customizer 创建，在用户已提供同类型 Bean 时退让。

两者的 `@ConditionalOnMissingBean` 独立生效，因此可以只替换注册表、只替换门面，或全部
替换。业务也始终可以直接注入 Spring AI 原生 `ChatModel`、`EmbeddingModel`、
`ChatClient`、`Advisor` 与 `VectorStore`。

应用没有任何模型 Bean 时仍可启动。首次调用 `chatClient()` / `chatModel()` 会抛出
`AiException`，错误码为 `AI_CHAT_MODEL_NOT_FOUND`；首次调用 `embeddingModel()` 会抛出
`AI_EMBEDDING_MODEL_NOT_FOUND`。这是必须由用户选择 Provider 或提供模型实现的扩展边界，
不是伪实现。

设置以下配置可完全关闭 Letool AI 自动配置，但不会关闭 Provider Starter 自己的模型 Bean：

```yaml
letool:
  ai:
    enabled: false
```

## 生产建议

- API Key 通过环境变量或密钥管理服务提供，不要提交到配置仓库；
- 通用重试使用 `spring.ai.retry.*`；连接/读取超时、连接池、代理和底层客户端参数
  按所选 Provider 官方文档配置，不假设所有 Provider 使用相同属性；
- 重试前确认工具调用和业务操作具备幂等性，避免重复副作用；
- 为多个模型配置明确的 Bean 名称和默认项，不依赖注册顺序；
- 对结构化输出、工具参数、RAG 检索结果和模型内容继续做业务校验；
- 使用真实 Provider sandbox 或受控账号进行契约测试，并为不可用、限流和超时建立降级策略；
- 不记录 API Key、完整提示词、个人信息或未经脱敏的模型响应。

## 破坏性迁移

本次重构删除了自研 Provider 与协议栈。迁移目标如下：

| 旧能力/API/配置 | 新方式 |
|---|---|
| `AiProvider` 及自定义 Provider 注册 | 引入 Spring AI Provider Starter，直接使用其 `ChatModel` / `EmbeddingModel` Bean |
| 自定义 `ChatMessage`、`ChatRequest`、`ChatResponse` | Spring AI `Prompt`、`Message`、`ChatOptions`、`ChatResponse`，或 `ChatClient` 流式 API |
| 自研 Provider HTTP、JDK 传输层和 SSE 解析 | 所选 Spring AI Provider 的原生客户端、流式与观测能力；通用重试使用 `spring.ai.retry.*`，超时/代理/底层客户端参数按 Provider 官方文档配置 |
| `EmbeddingService`、自定义 `EmbeddingRequest` / `EmbeddingResponse` | Spring AI `EmbeddingModel`、`EmbeddingRequest`、`EmbeddingResponse` |
| `@AiFunction`、`FunctionCallHandler`、`FunctionDefinition`、`FunctionCall` | Spring AI `@Tool`、`.tools(...)`、`ToolCallback` / `ToolCallbackProvider` |
| `ChatSession` | Spring AI Chat Memory Advisor，或由业务显式传入历史消息 |
| 自研 `PromptTemplate` | Spring AI `PromptTemplate` / `TemplateRenderer` 或 `ChatClient` 模板参数 |
| `RagService`、`DocumentLoader`、`TextSplitter`、自研 `VectorStore` | Spring AI ETL、Advisor、`VectorStore` 及对应生产级向量库 Starter |
| `letool.ai.default-provider` | `letool.ai.default-chat-model` / `default-embedding-model`，值改为 Spring Bean 名称 |
| `letool.ai.<provider>.*`、`custom-providers`、`chat.*`、`embedding.*`、`rate-limit.*` | 鉴权、端点和模型参数迁移至 `spring.ai.<provider>.*`，通用重试迁移至 `spring.ai.retry.*`，超时/代理/底层客户端参数按 Provider 官方文档迁移；Letool 不再接收这些配置 |
| 旧 `AiTemplate.chat()` / `.embedding()` 链式构建器 | `aiTemplate.chatClient(...)`、`chatModel(...)`、`embeddingModel(...)` 返回 Spring AI 原生类型 |

旧 API 与旧配置已移除，不提供兼容桥接。升级时应先加入 Provider Starter 和原生配置，
再替换 Java 调用，最后删除全部旧 `letool.ai` Provider 属性。

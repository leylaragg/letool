# letool-starter-ai

AI 集成模块，提供 OpenAI、DeepSeek、通义千问、智谱、Ollama 等多厂商的统一 API，支持对话、嵌入向量、Function Calling、RAG 检索增强生成和流式输出。

> ⚠️ OpenAI 兼容 provider 会发起真实 HTTP 请求，但统一超时、重试、流式、错误码细分、连接池和敏感日志保护仍需补齐。承载生产流量前请先补充这些控制和集成测试。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-ai</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 快速开始

**1. 引入依赖后，在 `application.yml` 中配置 API 密钥：**

```yaml
letool:
  ai:
    default-provider: openai
    openai:
      api-key: sk-your-key
    deepseek:
      api-key: sk-your-key
```

**2. 注入 `AiTemplate` 即可使用：**

```java
@Autowired
private AiTemplate aiTemplate;

public String chat() {
    ChatResponse resp = aiTemplate.chat()
        .provider("openai")
        .model("gpt-4o")
        .system("你是一个Java专家助手")
        .user("Spring Boot 如何配置多数据源？")
        .temperature(0.7)
        .maxTokens(4096)
        .execute();
    return resp.getContent();
}
```

## 配置属性

| 属性 | 默认值 | 说明 |
|------|--------|------|
| `letool.ai.enabled` | `true` | AI 模块总开关 |
| `letool.ai.default-provider` | `openai` | 默认提供商 |
| `letool.ai.openai.api-key` | - | OpenAI API 密钥 |
| `letool.ai.openai.base-url` | `https://api.openai.com/v1` | OpenAI 端点 |
| `letool.ai.openai.default-model` | `gpt-4o` | 默认模型 |
| `letool.ai.deepseek.api-key` | - | DeepSeek API 密钥 |
| `letool.ai.deepseek.default-model` | `deepseek-chat` | 默认模型 |
| `letool.ai.qwen.api-key` | - | 通义千问 API 密钥 |
| `letool.ai.qwen.default-model` | `qwen-max` | 默认模型 |
| `letool.ai.zhipu.api-key` | - | 智谱 API 密钥 |
| `letool.ai.ollama.base-url` | `http://localhost:11434` | Ollama 本地地址 |
| `letool.ai.ollama.default-model` | `llama3` | 默认模型 |
| `letool.ai.chat.default-temperature` | `0.7` | 默认温度 |
| `letool.ai.chat.default-max-tokens` | `4096` | 默认最大 Token |
| `letool.ai.embedding.default-model` | `text-embedding-3-small` | 嵌入默认模型 |
| `letool.ai.rate-limit.tokens-per-minute` | `100000` | 每分钟 Token 上限 |
| `letool.ai.rate-limit.requests-per-minute` | `100` | 每分钟请求上限 |

支持自定义提供商扩展：

```yaml
letool:
  ai:
    custom-providers:
      my-provider:
        api-key: xxx
        base-url: https://my-api.example.com/v1
        default-model: my-model
```

## 核心 API 示例

### 1. 对话（Chat）

**编程式（AiTemplate 链式 API）：**

```java
@Autowired
private AiTemplate aiTemplate;

// 基础对话
ChatResponse response = aiTemplate.chat()
    .provider("deepseek")
    .system("你是一个助手")
    .user("你好")
    .execute();

// 多轮对话（带历史）
ChatResponse response = aiTemplate.chat()
    .system("你是一个助手")
    .user("我叫张三")
    .assistant("你好张三，有什么可以帮你的？")
    .user("我叫什么名字？")
    .execute();

// 查询可用提供商和模型
List<String> providers = aiTemplate.listProviders();
List<String> models = aiTemplate.listModels("openai");
```

### 2. 嵌入向量（Embedding）

```java
// 批量嵌入
EmbeddingResponse resp = aiTemplate.embedding()
    .provider("openai")
    .model("text-embedding-3-small")
    .input("Hello World")
    .input("你好世界")
    .execute();

// 单条嵌入（直接返回 float[]）
float[] vector = aiTemplate.embedding()
    .input("Hello")
    .executeSingle();
```

### 3. Function Calling

**注解式（`@AiFunction` 标记方法）：**

```java
@Component
public class WeatherService {

    @AiFunction(name = "get_weather", description = "获取指定城市的天气信息")
    public String getWeather(String city) {
        return city + "今天晴，25°C";
    }
}
```

**编程式（`FunctionCallHandler` 注册和执行）：**

```java
FunctionCallHandler handler = new FunctionCallHandler();

// 注册函数
handler.register("get_weather", "获取指定城市的天气信息", params -> {
    String city = (String) params.get("city");
    return city + "今天晴，25°C";
});

// 获取函数定义传给 LLM
List<FunctionDefinition> defs = handler.getFunctionDefinitions();
ChatResponse resp = aiTemplate.chat()
    .system("你是助手")
    .user("北京天气怎么样？")
    .functions(defs)
    .execute();

// 解析 LLM 返回的函数调用
FunctionCall call = resp.getFunctionCall();
if (call != null) {
    String result = handler.invoke(call.getName(), call.getArguments());
    // 将结果回传 LLM 生成最终回答
    ChatResponse finalResp = aiTemplate.chat()
        .messages(previousMessages)
        .function(call.getName(), result)
        .execute();
}
```

### 4. RAG 检索增强生成

```java
@Autowired
private RagService ragService;

// 索引知识库
ragService.index("Spring Boot 是一个 Java 框架...");
ragService.indexDocument("/data/knowledge.pdf");
ragService.indexDirectory("/data/docs");

// RAG 查询
String answer = ragService.query("什么是 Spring Boot？");
String answer = ragService.query("什么是 Spring Boot？", 10);  // 指定 TopK

// 仅检索，不调用 LLM
List<VectorStore.SearchResult> results = ragService.retrieve("微服务");

// 管理
int count = ragService.getEntryCount();
ragService.clearIndex();
```

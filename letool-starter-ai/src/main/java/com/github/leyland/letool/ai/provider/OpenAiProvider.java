package com.github.leyland.letool.ai.provider;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.github.leyland.letool.ai.config.AiProperties;
import com.github.leyland.letool.ai.core.*;
import com.github.leyland.letool.ai.exception.AiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/**
 * OpenAI 提供商实现 —— 通过 HTTP 调用 OpenAI 兼容 API.
 *
 * <h3>兼容性</h3>
 * <p>此实现使用标准的 OpenAI Chat Completions API 格式，
 * 因此与所有 OpenAI 兼容的 API 提供商兼容（DeepSeek、通义千问 等）.</p>
 *
 * <h3>API 端点</h3>
 * <ul>
 *   <li>对话：{@code POST /chat/completions}</li>
 *   <li>嵌入：{@code POST /embeddings}</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 */
public class OpenAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiProvider.class);

    // ======================== 常量 ========================

    /**
     * 提供商名称
     */
    public static final String PROVIDER_NAME = "openai";

    /**
     * 对话 API 路径
     */
    private static final String CHAT_PATH = "/chat/completions";
    /**
     * 嵌入 API 路径
     */
    private static final String EMBEDDING_PATH = "/embeddings";

    // ======================== 字段 ========================

    /**
     * 提供商配置
     */
    protected final AiProperties.Provider config;

    /**
     * 默认模型
     */
    protected final String defaultModel;

    // ======================== 构造方法 ========================

    /**
     * 创建 OpenAI 提供商实例.
     *
     * @param config OpenAI 配置属性
     */
    public OpenAiProvider(AiProperties.Provider config) {
        this.config = config;
        this.defaultModel = config.getDefaultModel() != null ? config.getDefaultModel() : "gpt-4o";
    }

    // ======================== 核心方法：对话 ========================

    /**
     * 发送对话请求到 OpenAI API.
     *
     * <p>构建 JSON 请求体，通过 HTTP POST 发送到 {@code /chat/completions} 端点，
     * 解析返回的 JSON 响应.</p>
     *
     * @param request 对话请求
     * @return 对话响应
     * @throws AiException 当 API 调用失败时
     */
    @Override
    public ChatResponse chat(ChatRequest request) {
        checkApiKey();
        long startTime = System.currentTimeMillis();

        try {
            // 构建请求体
            String model = request.getModel() != null ? request.getModel() : defaultModel;
            String jsonBody = buildChatRequestBody(request, model);

            // 发送 HTTP 请求
            String apiUrl = getBaseUrl() + CHAT_PATH;
            String responseBody = httpPost(apiUrl, jsonBody);

            // 解析响应
            ChatResponse response = parseChatResponse(responseBody);
            response.setProvider(getProviderName());
            response.setLatencyMs(System.currentTimeMillis() - startTime);

            log.debug("OpenAI chat 调用成功: model={}, tokens={}, latency={}ms",
                    model,
                    response.getUsage() != null ? response.getUsage().getTotalTokens() : 0,
                    response.getLatencyMs());

            return response;
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiException("OpenAI 对话请求失败: " + redactSensitive(e.getMessage()), getProviderName(), e);
        }
    }

    /**
     * 发送流式对话请求到 OpenAI 兼容 API。
     *
     * <p>解析 {@code text/event-stream} 中的 {@code data: ...} 行，提取
     * {@code choices[0].delta.content} 并按顺序回调给调用方。</p>
     *
     * @param request 对话请求
     * @param onDelta 增量文本回调
     * @throws AiException 当 API 调用失败时
     */
    @Override
    public void chatStream(ChatRequest request, Consumer<String> onDelta) {
        checkApiKey();
        if (onDelta == null) {
            throw new AiException("OpenAI 流式回调不能为空", getProviderName());
        }

        try {
            String model = request.getModel() != null ? request.getModel() : defaultModel;
            String jsonBody = buildChatRequestBody(request, model, true);
            String apiUrl = getBaseUrl() + CHAT_PATH;
            httpPostStream(apiUrl, jsonBody, onDelta);
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiException("OpenAI 流式对话请求失败: " + redactSensitive(e.getMessage()), getProviderName(), e);
        }
    }

    // ======================== 核心方法：嵌入 ========================

    /**
     * 获取文本向量（嵌入）.
     *
     * @param request 嵌入请求
     * @return 嵌入响应
     * @throws AiException 当 API 调用失败时
     */
    @Override
    public EmbeddingResponse embedding(EmbeddingRequest request) {
        checkApiKey();
        try {
            String model = request.getModel() != null ? request.getModel() : "text-embedding-3-small";
            String jsonBody = buildEmbeddingRequestBody(request, model);

            String apiUrl = getBaseUrl() + EMBEDDING_PATH;
            String responseBody = httpPost(apiUrl, jsonBody);

            return parseEmbeddingResponse(responseBody);
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiException("OpenAI 嵌入请求失败: " + redactSensitive(e.getMessage()), getProviderName(), e);
        }
    }

    // ======================== 元信息 ========================

    /**
     * 获取提供商名称.
     *
     * @return "openai"
     */
    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    /**
     * 获取可用模型列表.
     *
     * <p>返回一些常用的 OpenAI 模型名称.</p>
     *
     * @return 模型名称列表
     */
    @Override
    public List<String> getAvailableModels() {
        return Arrays.asList("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-4", "gpt-3.5-turbo",
                "text-embedding-3-small", "text-embedding-3-large", "text-embedding-ada-002");
    }

    /**
     * 检查提供商是否可用.
     *
     * @return {@code true} 如果 API 密钥已配置
     */
    @Override
    public boolean isAvailable() {
        return config.getApiKey() != null && !config.getApiKey().isEmpty();
    }

    // ======================== 受保护的方法（子类可覆盖） ========================

    /**
     * 获取 API 基础 URL.
     *
     * @return API 基础 URL，去除末尾斜杠
     */
    protected String getBaseUrl() {
        String url = config.getBaseUrl();
        if (url != null && url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    /**
     * 检查 API 密钥是否已配置.
     *
     * @throws AiException 如果密钥未配置
     */
    protected void checkApiKey() {
        if (config.getApiKey() == null || config.getApiKey().isEmpty()) {
            throw new AiException("OpenAI API 密钥未配置，请在 letool.ai.openai.api-key 中设置", getProviderName());
        }
    }

    // ======================== 请求体构建 ========================

    /**
     * 构建对话请求的 JSON 请求体.
     *
     * @param request 对话请求
     * @param model   模型名称
     * @return JSON 字符串
     */
    protected String buildChatRequestBody(ChatRequest request, String model) {
        return buildChatRequestBody(request, model, false);
    }

    /**
     * 构建对话请求的 JSON 请求体.
     *
     * @param request 对话请求
     * @param model   模型名称
     * @param stream  是否启用流式输出
     * @return JSON 字符串
     */
    protected String buildChatRequestBody(ChatRequest request, String model, boolean stream) {
        JSONObject body = new JSONObject();
        body.put("model", model);
        if (stream) {
            body.put("stream", true);
        }

        // 消息列表
        JSONArray messages = new JSONArray();
        if (request.getMessages() != null) {
            for (ChatMessage msg : request.getMessages()) {
                JSONObject msgObj = new JSONObject();
                msgObj.put("role", msg.getRole());
                msgObj.put("content", msg.getContent());
                if (msg.getName() != null) {
                    msgObj.put("name", msg.getName());
                }
                if (msg.getFunctionCall() != null) {
                    JSONObject fc = new JSONObject();
                    fc.put("name", msg.getFunctionCall().getName());
                    fc.put("arguments", msg.getFunctionCall().getArguments());
                    msgObj.put("function_call", fc);
                }
                messages.add(msgObj);
            }
        }
        body.put("messages", messages);

        // 温度
        if (request.getTemperature() > 0) {
            body.put("temperature", request.getTemperature());
        }

        // 最大 Token 数
        if (request.getMaxTokens() > 0) {
            body.put("max_tokens", request.getMaxTokens());
        }

        // 函数定义（Function Calling）
        if (request.getFunctions() != null && !request.getFunctions().isEmpty()) {
            JSONArray functions = new JSONArray();
            for (FunctionDefinition def : request.getFunctions()) {
                JSONObject funcObj = new JSONObject();
                funcObj.put("name", def.getName());
                funcObj.put("description", def.getDescription());
                if (def.getParameters() != null) {
                    funcObj.put("parameters", def.getParameters());
                }
                functions.add(funcObj);
            }
            body.put("functions", functions);
        }

        return body.toJSONString();
    }

    /**
     * 构建嵌入请求的 JSON 请求体.
     *
     * @param request 嵌入请求
     * @param model   模型名称
     * @return JSON 字符串
     */
    protected String buildEmbeddingRequestBody(EmbeddingRequest request, String model) {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("input", request.getInput());
        return body.toJSONString();
    }

    // ======================== 响应解析 ========================

    /**
     * 解析对话 API 的 JSON 响应.
     *
     * @param responseBody JSON 响应字符串
     * @return {@link ChatResponse} 对象
     */
    protected ChatResponse parseChatResponse(String responseBody) {
        JSONObject json = JSON.parseObject(responseBody);

        ChatResponse response = new ChatResponse();
        response.setId(json.getString("id"));
        response.setModel(json.getString("model"));

        // 解析 choices
        JSONArray choices = json.getJSONArray("choices");
        if (choices != null && !choices.isEmpty()) {
            JSONObject choice = choices.getJSONObject(0);
            ChatResponse.ChatChoice chatChoice = new ChatResponse.ChatChoice();
            JSONObject message = choice.getJSONObject("message");
            if (message != null) {
                chatChoice.setRole(message.getString("role"));
                chatChoice.setContent(message.getString("content"));

                // 函数调用
                JSONObject functionCall = message.getJSONObject("function_call");
                if (functionCall != null) {
                    FunctionCall fc = new FunctionCall();
                    fc.setName(functionCall.getString("name"));
                    fc.setArguments(functionCall.getString("arguments"));
                    chatChoice.setFunctionCall(fc);
                }
            }
            chatChoice.setFinishReason(choice.getString("finish_reason"));
            response.setChoice(chatChoice);
        }

        // 解析 usage
        JSONObject usage = json.getJSONObject("usage");
        if (usage != null) {
            ChatResponse.Usage u = new ChatResponse.Usage();
            u.setPromptTokens(usage.getIntValue("prompt_tokens"));
            u.setCompletionTokens(usage.getIntValue("completion_tokens"));
            u.setTotalTokens(usage.getIntValue("total_tokens"));
            response.setUsage(u);
        }

        return response;
    }

    /**
     * 解析嵌入 API 的 JSON 响应.
     *
     * @param responseBody JSON 响应字符串
     * @return {@link EmbeddingResponse} 对象
     */
    protected EmbeddingResponse parseEmbeddingResponse(String responseBody) {
        JSONObject json = JSON.parseObject(responseBody);

        EmbeddingResponse response = new EmbeddingResponse();
        JSONArray data = json.getJSONArray("data");
        if (data != null) {
            List<EmbeddingResponse.EmbeddingData> embeddingDataList = new ArrayList<>();
            for (int i = 0; i < data.size(); i++) {
                JSONObject item = data.getJSONObject(i);
                EmbeddingResponse.EmbeddingData ed = new EmbeddingResponse.EmbeddingData();
                ed.setIndex(item.getIntValue("index"));

                JSONArray embArray = item.getJSONArray("embedding");
                if (embArray != null) {
                    float[] embedding = new float[embArray.size()];
                    for (int j = 0; j < embArray.size(); j++) {
                        embedding[j] = embArray.getFloatValue(j);
                    }
                    ed.setEmbedding(embedding);
                }
                embeddingDataList.add(ed);
            }
            response.setData(embeddingDataList);
        }

        // 解析 usage
        JSONObject usage = json.getJSONObject("usage");
        if (usage != null) {
            ChatResponse.Usage u = new ChatResponse.Usage();
            u.setPromptTokens(usage.getIntValue("prompt_tokens"));
            u.setTotalTokens(usage.getIntValue("total_tokens"));
            response.setUsage(u);
        }

        return response;
    }

    // ======================== HTTP 工具方法 ========================

    /**
     * 发送 HTTP POST 请求.
     *
     * @param apiUrl   API URL
     * @param jsonBody JSON 请求体
     * @return 响应体字符串
     * @throws IOException 当网络请求失败时
     * @throws AiException 当 API 返回非 2xx 状态码时
     */
    protected String httpPost(String apiUrl, String jsonBody) throws IOException {
        int maxRetries = Math.max(0, config.getMaxRetries());
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return doHttpPost(apiUrl, jsonBody);
            } catch (SocketTimeoutException e) {
                if (attempt >= maxRetries) {
                    throw new AiException("OpenAI 请求超时: " + redactSensitive(e.getMessage()), getProviderName(), e);
                }
                sleepBeforeRetry(attempt);
            } catch (AiException e) {
                if (!isRetryableStatus(e.getStatusCode()) || attempt >= maxRetries) {
                    throw e;
                }
                sleepBeforeRetry(attempt);
            } catch (IOException e) {
                if (attempt >= maxRetries) {
                    throw e;
                }
                sleepBeforeRetry(attempt);
            }
        }
        throw new AiException("OpenAI 请求失败: 已耗尽重试次数", getProviderName());
    }

    /**
     * 发送流式 HTTP POST 请求。
     *
     * @param apiUrl   API URL
     * @param jsonBody JSON 请求体
     * @param onDelta  增量文本回调
     * @throws IOException 当网络请求失败时
     */
    protected void httpPostStream(String apiUrl, String jsonBody, Consumer<String> onDelta) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = URI.create(apiUrl).toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(normalizeTimeout(config.getConnectTimeoutMillis(), 10000));
            conn.setReadTimeout(normalizeTimeout(config.getReadTimeoutMillis(), 60000));
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
            conn.setRequestProperty("Accept", "text/event-stream");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            int statusCode = conn.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                String errorBody;
                try (java.io.InputStream es = conn.getErrorStream()) {
                    errorBody = es != null ? new String(es.readAllBytes(), StandardCharsets.UTF_8) : "";
                }
                throw buildApiException(statusCode, errorBody);
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    handleStreamLine(line, onDelta);
                }
            }
        } catch (SocketTimeoutException e) {
            throw new AiException("OpenAI 流式请求超时: " + redactSensitive(e.getMessage()), getProviderName(), e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 处理单行 SSE 数据。
     *
     * @param line    SSE 行
     * @param onDelta 增量文本回调
     */
    private void handleStreamLine(String line, Consumer<String> onDelta) {
        if (line == null || line.isBlank() || !line.startsWith("data:")) {
            return;
        }

        String data = line.substring("data:".length()).trim();
        if ("[DONE]".equals(data)) {
            return;
        }

        JSONObject json = JSON.parseObject(data);
        JSONArray choices = json.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return;
        }

        JSONObject choice = choices.getJSONObject(0);
        if (choice == null) {
            return;
        }

        JSONObject delta = choice.getJSONObject("delta");
        if (delta == null) {
            return;
        }

        String content = delta.getString("content");
        if (content != null && !content.isEmpty()) {
            onDelta.accept(content);
        }
    }

    /**
     * 执行单次 HTTP POST 请求.
     *
     * @param apiUrl   API URL
     * @param jsonBody JSON 请求体
     * @return 响应体字符串
     * @throws IOException 当网络请求失败时
     * @throws AiException 当 API 返回非 2xx 状态码时
     */
    private String doHttpPost(String apiUrl, String jsonBody) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = URI.create(apiUrl).toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(normalizeTimeout(config.getConnectTimeoutMillis(), 10000));
            conn.setReadTimeout(normalizeTimeout(config.getReadTimeoutMillis(), 60000));
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
            conn.setRequestProperty("Accept", "application/json");

            // 写请求体
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            int statusCode = conn.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                String errorBody;
                try (java.io.InputStream es = conn.getErrorStream()) {
                    errorBody = es != null ? new String(es.readAllBytes(), StandardCharsets.UTF_8) : "";
                }
                throw buildApiException(statusCode, errorBody);
            }

            try (java.io.InputStream is = conn.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 构建结构化 API 异常，提取 OpenAI 兼容错误对象并执行敏感信息脱敏。
     *
     * @param statusCode HTTP 状态码
     * @param errorBody  上游错误响应体
     * @return AI 异常
     */
    private AiException buildApiException(int statusCode, String errorBody) {
        String errorMessage = errorBody;
        String errorType = null;
        String errorCode = null;

        try {
            JSONObject json = JSON.parseObject(errorBody);
            JSONObject error = json != null ? json.getJSONObject("error") : null;
            if (error != null) {
                errorMessage = error.getString("message");
                errorType = error.getString("type");
                errorCode = error.getString("code");
            }
        } catch (Exception ignored) {
            // 非 JSON 错误响应保留原始响应体，后续统一脱敏。
        }

        String message = String.format("OpenAI API 返回错误 (HTTP %d, code=%s, type=%s): %s",
                statusCode,
                errorCode != null ? errorCode : "unknown",
                errorType != null ? errorType : "unknown",
                redactSensitive(errorMessage));
        return new AiException(statusCode, message, getProviderName(), errorCode, errorType);
    }

    /**
     * 判断 HTTP 状态是否适合重试。
     *
     * @param statusCode HTTP 状态码
     * @return {@code true} 表示临时错误，可按配置重试
     */
    private boolean isRetryableStatus(int statusCode) {
        return statusCode == 408 || statusCode == 429 || statusCode >= 500;
    }

    /**
     * 重试前退避等待。
     *
     * @param attempt 当前尝试次数（从 0 开始）
     */
    private void sleepBeforeRetry(int attempt) {
        long backoff = Math.max(0, config.getRetryBackoffMillis());
        if (backoff == 0) {
            return;
        }
        try {
            Thread.sleep(backoff * (attempt + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException("OpenAI 请求重试等待被中断", getProviderName(), e);
        }
    }

    /**
     * 归一化超时配置，避免 0 或负数导致无期限等待。
     *
     * @param value        配置值
     * @param defaultValue 默认值
     * @return 可用于 {@link HttpURLConnection} 的超时时间
     */
    private int normalizeTimeout(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
    }

    /**
     * 对错误消息中的 API key 和 Bearer token 进行脱敏。
     *
     * @param value 原始文本
     * @return 脱敏后的文本
     */
    protected String redactSensitive(String value) {
        if (value == null) {
            return null;
        }
        String redacted = value;
        String apiKey = config.getApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            redacted = redacted.replace(apiKey, "[REDACTED]");
        }
        redacted = redacted.replaceAll("(?i)Bearer\\s+[A-Za-z0-9._\\-]+", "Bearer [REDACTED]");
        redacted = redacted.replaceAll("sk-[A-Za-z0-9._\\-]+", "sk-[REDACTED]");
        return redacted;
    }
}

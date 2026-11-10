# demo-springboot-ai

基于 **Spring AI 2.0.0** 的 AI 应用 Demo：大模型对话（同步/流式/结构化输出）、RAG 检索增强生成（文档入库 → 向量化 → 检索问答）、多模型配置（对话模型与嵌入模型分离）、以及完整的可观测性体系（Metrics / Tracing / JSON 日志 / 告警规则）。

## 一、技术栈

| 分类 | 技术 | 说明 |
|---|---|---|
| 基础框架 | Spring Boot **4.1.0** + Java **21** | 启用 JDK 虚拟线程（`spring.threads.virtual.enabled: true`） |
| AI 框架 | Spring AI **2.0.0**（BOM 管理） | `spring-ai-client-chat`、`spring-ai-rag`、`spring-ai-starter-tool-search-advisor` |
| 对话模型 | DeepSeek（deepseek-v4-pro） | 通过 `spring-ai-starter-model-openai` 走 OpenAI 兼容协议（base-url 指向 DeepSeek） |
| 嵌入模型 | 本地 llama-server（qwen3-embedding:0.6b） | `spring-ai-starter-model-ollama` + OpenAI 兼容端点；DeepSeek 不支持 embedding，故对话/嵌入模型分离 |
| 向量存储 | Redis + RedisVectorStore | Jedis 客户端 + 自定义索引（`custom-index`），启动自动建索引 |
| 缓存/批处理 | Redisson 3.17.7 | RBatch 管道批量设置 TTL / 回滚删除 |
| 连接池 | Jedis 7.x + Lettuce | Jedis 供向量库，Lettuce/JedisConnectionFactory 供 Spring Data，Redisson 供批处理 |
| 文档处理 | spring-ai-markdown-document-reader + spring-ai-tika-document-reader | Markdown / DOCX / PDF 读取 |
| 可观测性 | Actuator + Micrometer Prometheus + Micrometer Tracing(OTel) + logstash-logback-encoder + AspectJ | 指标、追踪、结构化 JSON 日志、@Timed/@Counted 注解 |
| 外部监控 | Prometheus（prometheus.yml）+ alerts.yml 告警规则 | 服务宕机 / 5xx 错误率 / P95 延迟告警 |
| 其他 | Lombok、Gson、spring-boot-devtools | Gson 含 LocalDateTime 序列化适配器 |

## 二、实现的功能

### 1. AI 对话（`/api/chat/*`）

| 接口 | 方法 | 功能 |
|---|---|---|
| `/api/chat/chat?message=` | POST | 普通对话：调用 DeepSeek 返回文本 |
| `/api/chat/chatStream?message=` | POST | **SSE 流式对话**：`Flux<String>` 逐 token 返回 |
| `/api/chat/getInfo?type=` | POST | **结构化输出**：让 LLM 推荐电影并自动反序列化为 `List<MovieInfoVO>`（title/type/year/rating/reason），使用 `ParameterizedTypeReference` 泛型解析 |

### 2. RAG 检索增强生成

**文档入库流水线（`POST /load`）**——完整 RAG 数据预处理链路：

```
MarkdownDocumentReader 读取 classpath:agent.md
  → TokenTextSplitter 分块（2000 字符/块，支持中英文标点分隔）
  → KeywordMetadataEnricher 提取 5 个关键词（LLM）
  → ChineseSummaryMetadataEnricher 生成中文摘要（自定义组件，替代内置英文摘要器）
  → EmbeddingModel 向量化
  → RedisVectorStore 批量入库
  → Redisson 管道批量设置 TTL（1 小时）
  → 入库失败自动回滚（批量删除已写缓存）
```

**检索与问答：**

| 接口 | 方法 | 功能 |
|---|---|---|
| `/ask` | POST | RAG 问答：`MessageChatMemoryAdvisor`（对话记忆）+ `RetrievalAugmentationAdvisor`（向量检索 topK=3、相似度阈值 0.5）双 Advisor 链；conversationId 动态传参；外层包裹 Micrometer Observation 埋点 |
| `/embed?text=` | GET | 显式调用嵌入模型，返回 `EmbeddingResponse`（向量数据） |
| `/search` | POST | 向量相似度检索，返回命中的 `Document` 列表（含元数据与向量） |

**意图识别路由**（`RagController.rag()`，prompt 驱动）：让 LLM 分析用户输入，识别 `rag`（知识问答）/ `general`（通用对话）/ `greeting`（问候）三种意图并分发到对应处理器——手写版的意图识别，注释中说明了可替换为 Spring AI Alibaba 的 IntentClassificationNode。

### 3. 多模型配置与注入（`AiModelConfig` / `VectorStoreConfig` / `RedisConfig`）

- **对话与嵌入模型分离**：DeepSeek 负责对话（OpenAI 兼容），本地 llama-server 负责 embedding（DeepSeek 暂无 embedding 能力）
- **禁用自动配置、全部手动 @Bean**：`spring.ai.chat.client.enabled: false` + 排除 `RedisVectorStoreAutoConfiguration`，避免多模型自动装配冲突
- **@Qualifier 区分多模型实例**：`openAiChatModel` / `openAiEmbeddingModel` / `openAiChatClient` / `myVectorStore`
- **对话记忆**：`MessageWindowChatMemory`（窗口 20 条消息），`ChatMemory.CONVERSATION_ID` 由调用方动态传参实现多会话隔离
- **三个 Redis 客户端并存**：Jedis `RedisClient`（向量库底层）、`JedisConnectionFactory`（Spring Data 连接池）、`RedissonClient`（管道批处理）——各自职责清晰

### 4. 可观测性（完整闭环）

| 能力 | 实现 |
|---|---|
| 指标 | Actuator 暴露 `health/info/metrics/prometheus/env/loggers/threaddump/heapdump`；HTTP 请求 P50-P99 分位数直方图 + SLO（50ms~1s）；Spring AI ChatClient 调用时延指标；`application`/`instance` 全局标签 |
| 健康检查 | Redis 连通性、磁盘空间（<100MB 告警）、Kubernetes liveness/readiness 探针（`/actuator/health/liveness`） |
| 追踪 | Micrometer Tracing + OpenTelemetry：W3C TraceContext 传播、OTLP HTTP 导出（对接 Jaeger/Collector/Langfuse）、日志同步走 OTel pipeline；控制台日志带 `%X{traceId}` |
| 埋点 | Micrometer Observation 手写埋点（RAG 查询）+ 自定义 `RAGObservationHandle` 打印检索命中内容（截取前 30 字符防刷屏）+ `@Timed`/`@Counted` 注解支持 |
| 日志 | `logback-spring.xml` 双输出：控制台彩色（含 traceId）+ JSON 结构化滚动文件（`logs/application.json`，Loki/ELK 直接采集） |
| 告警 | `prometheus.yml`（15s 抓取 `/actuator/prometheus`）+ `alerts.yml`：服务宕机 1 分钟、5xx 错误率 >5% 持续 2 分钟、P95 延迟 >1s 持续 2 分钟 |

### 5. 工具类

- `GsonUtil`：对象/List/Map 与 JSON 互转，注册 `LocalDateTimeAdapter`（`yyyy-MM-dd HH:mm:ss` 序列化，避免 Linux 环境时间格式问题）

## 三、运行要求

| 依赖 | 说明 |
|---|---|
| DeepSeek API Key | `application.yml` 的 `spring.ai.openai.api-key`（已脱敏为占位符，填写自己的 key），base-url 指向 `https://api.deepseek.com/v1` |
| 本地 llama-server / Ollama | 嵌入模型 `qwen3-embedding:0.6b`，监听 `http://localhost:11434/v1`（llama-server 需带 /v1 路径） |
| Redis | 向量库 + 会话缓存，在 `application.yml` 的 `spring.data.redis` 填写 host/port/password（已脱敏） |
| 知识库文档 | `POST /load` 读取 `classpath:agent.md`，需在 `src/main/resources/` 下放置该 Markdown |
| （可选）OTel 后端 | Jaeger / OTel Collector，OTLP HTTP 端点 `http://localhost:4318/v1/traces` |
| （可选）Prometheus | `docker run -d -p 9090:9090 -v ./prometheus.yml:/etc/prometheus/prometheus.yml prom/prometheus` |

> 密钥约定：仓库中的配置不含任何真实密钥。本地开发可在 `application.yml` 填自己的 key（`application-local.yml` 已在 .gitignore 中，可放本地私有配置），生产建议用环境变量注入。

启动：`mvn spring-boot:run`，端口 8080。

## 四、项目结构

```
demo-springboot-ai/
├── pom.xml                          # Spring Boot 4.1.0 + Spring AI 2.0.0 BOM
├── prometheus.yml                   # Prometheus 抓取配置
├── alerts.yml                       # 告警规则（宕机/错误率/延迟）
└── src/main/
    ├── java/com/example/demo/
    │   ├── DemoApplication.java
    │   ├── controller/
    │   │   ├── ChatController.java      # 对话 / 流式 / 结构化输出
    │   │   └── RagController.java       # RAG 问答 / 入库流水线 / 检索 / 意图路由
    │   ├── config/
    │   │   ├── AiModelConfig.java       # ChatClient + 对话记忆 + RAG Advisor 链
    │   │   ├── VectorStoreConfig.java   # RedisVectorStore 手动装配
    │   │   ├── RedisConfig.java         # Jedis / Lettuce / Redisson 三客户端
    │   │   └── ObConfig.java            # ObservationRegistry + 自定义 Handler
    │   ├── handler/
    │   │   ├── ChineseSummaryMetadataEnricher.java  # 中文摘要元数据增强（自定义 DocumentTransformer）
    │   │   └── RAGObservationHandle.java            # 向量检索观测：打印命中内容
    │   ├── dto/  RagRequestDTO.java
    │   ├── vo/   MovieInfoVO.java       # 结构化输出反序列化目标
    │   ├── util/ GsonUtil.java
    │   ├── adapter/ LocalDateTimeAdapter.java
    │   └── InitDataRunner.java          # 启动时初始化向量数据（注释备用）
    └── resources/
        ├── application.yml              # 多模型/Redis/可观测性全套配置
        ├── logback-spring.xml           # 控制台 + JSON 结构化双输出
        └── static/
```

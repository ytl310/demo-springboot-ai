package com.example.demo.controller;

import com.example.demo.dto.RagRequestDTO;
import com.example.demo.util.GsonUtil;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.BatchResult;
import org.redisson.api.RBatch;
import org.redisson.api.RBucketAsync;
import org.redisson.api.RedissonClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author ytl
 * @date 2026/6/30 16:35
 * @description TODO
 */
@RestController
@Slf4j
public class RagController {

    private final ChatClient openAiChatClient;
    private final VectorStore vectorStore;
    private final DocumentTransformer documentTransformer;
    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final ChatMemory chatMemory;
    private static final int TTL_IN_SECONDS = 3600;
    private static final String KEY_PREFIX = "embedding-key:";
    private final RedissonClient redissonClient;
    private final ObservationRegistry observationRegistry;

    //注入的地方还是要使用标识来区分哪一个模型的，这里使用@Qualifier
    public RagController(@Qualifier("openAiChatClient") ChatClient openAiChatClient,
                         VectorStore vectorStore, @Qualifier("chineseSummaryMetadataEnricher") DocumentTransformer documentTransformer,
                         @Qualifier("openAiChatModel") ChatModel chatModel,
                         @Qualifier("openAiEmbeddingModel") EmbeddingModel embeddingModel,
                         ChatMemory chatMemory, RedissonClient redissonClient, ObservationRegistry observationRegistry) {
        this.openAiChatClient = openAiChatClient;
        this.vectorStore = vectorStore;
        this.documentTransformer = documentTransformer;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.chatMemory = chatMemory;
        this.redissonClient = redissonClient;
        this.observationRegistry = observationRegistry;
    }

    @PostMapping("/ask")
    public String ask(@RequestBody RagRequestDTO dto) {
        //创建Observation 并执行监测
        return Observation.createNotStarted("user.name", observationRegistry)
                .contextualName("getting-user-name")
                .lowCardinalityKeyValue("userType", "userType1")
                .highCardinalityKeyValue("userId", "1234")
                .observe(() -> {
                    String conversationId = "1";
                    return openAiChatClient.prompt()
//                .advisors(ragAdvisor)
//                .advisors(a -> a.advisors(MessageChatMemoryAdvisor.builder(chatMemory).build()
////                        , QuestionAnswerAdvisor.builder(vectorStore).build()
//                        ).param(ChatMemory.CONVERSATION_ID, 1))
                            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                            .user(dto.getQuestion())
                            .call()
                            .content();

                });
    }

    @GetMapping("/embed")
    public EmbeddingResponse getEmbedding(@RequestParam String text) {
        // 显式调用 embedding 模型
        return embeddingModel.embedForResponse(List.of(text));
    }

    // 或者返回检索到的文档及其向量
    @PostMapping("/search")
    public List<Document> search(@RequestBody RagRequestDTO dto) {
        return vectorStore.similaritySearch(dto.getQuestion());
        // 返回的 Document 对象中包含 .getEmbedding() 方法
    }

    @PostMapping("/load")
    public Boolean loadDocuments() {
        // Step 1: 读取文档并分块
        MarkdownDocumentReader reader = new MarkdownDocumentReader("classpath:agent.md");
        List<Document> documents = reader.get();

        // 使用 Token 分割器（适合长文本）
        // 使用默认的标点符号列表
        List<Character> defaultPunctuationMarks = List.of('。', '，', '！', '？',
                '；', '：', '.', ',', '!', '?', ';', ':');
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(2000)
                .withMinChunkSizeChars(1024)
                .withMinChunkLengthToEmbed(10)
                .withMaxNumChunks(10000)
                .withKeepSeparator(true)
                .withPunctuationMarks(defaultPunctuationMarks)
                .build();

        List<Document> chunks = splitter.transform(documents);

        // Step 2: 元数据增强 —— 关键词 + 中文摘要
        List<Document> enrichedDocs = KeywordMetadataEnricher.builder(chatModel)
                .keywordCount(5)
                .build()
                .andThen(documentTransformer)
                .apply(chunks);


        // Step 3: 提取文档ID并批量设置过期时间
        List<String> ids = extractDocumentIds(enrichedDocs);

        // Step 4: 生成向量并存入向量数据库,其中内部自动embedding将文本转为向量（事务性操作）
        try {
            vectorStore.add(enrichedDocs);

            // Step 5: 批量设置Redis过期时间（入库成功后执行）
            batchSetExpire(ids);

        } catch (Exception e) {
            log.error("向量入库失败，回滚Redis缓存", e);
            // 入库失败，清除可能已设置的Redis缓存
            batchDeleteCache(ids);
            throw new RuntimeException("文档处理失败", e);
        }
        return Boolean.TRUE;

    }

    // 如果你的Document类有getId()方法
    private List<String> extractDocumentIds(List<Document> documents) {
        return documents.stream()
                .map(Document::getId)
                .filter(StringUtils::isNotBlank)
                .toList();
    }

    /**
     * 批量设置过期时间
     */
    private void batchSetExpire(List<String> ids) {
        if (ids.isEmpty()) {
            return;
        }

        try {
            // 创建批量操作
            RBatch batch = redissonClient.createBatch();

            // 批量添加expire命令
            for (String id : ids) {
                String key = KEY_PREFIX + id;
                RBucketAsync<Object> bucket = batch.getBucket(key);
                bucket.expireAsync(Duration.ofSeconds(RagController.TTL_IN_SECONDS));
            }

            // 执行批量操作
            BatchResult<?> result = batch.execute();
            // 获取成功数量（Redisson的BatchResult不直接返回每个命令的成功状态）
            // 方法1：如果不需要精确统计，直接记录总数
            log.info("设置过期时间完成，总数: {}", ids.size());

            // 方法2：如果需要精确统计，使用RFuture逐个检查
            // 但这种方式会失去批量操作的优势

        } catch (Exception e) {
            log.error("批量设置过期时间失败", e);
        }
    }

    /**
     * 批量删除缓存
     */
    private void batchDeleteCache(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        try {
            // 构建完整的Key列表
            List<String> keys = ids.stream()
                    .map(id -> KEY_PREFIX + id)
                    .toList();

            // 创建批量操作
            RBatch batch = redissonClient.createBatch();

            // 批量添加删除命令
            for (String key : keys) {
                RBucketAsync<Object> bucket = batch.getBucket(key);
                bucket.deleteAsync();
            }

            // 执行批量操作
            BatchResult<?> result = batch.execute();

            // 获取删除结果
            List<?> responses = result.getResponses();
            long deletedCount = responses.stream()
                    .filter(r -> r instanceof Boolean && Boolean.TRUE.equals(r))
                    .count();

            log.info("删除缓存完成，成功删除: {}/{}", deletedCount, keys.size());

        } catch (Exception e) {
            log.error("批量删除缓存失败", e);
        }
    }


    /**
     * @param [userInput]
     * @return java.lang.String
     * @author ytl
     * @date 2026/7/3 17:31
     * @description 下面是基于LLMprompt自动做意图识别，
     * 也可以基于spring ai alibaba的IntentClassificationNode意图识别(当然可能都是基于prompt提示词自动意图识别)
     **/
    public String rag(String userInput) {
        // 构建意图识别的 Prompt
        String intentPrompt = String.format("""
                你是一个智能客服意图识别助手。
                请分析用户的输入，识别其应该分配给哪个处理器。
                可选的处理器类型：rag（知识问答）、general（通用对话）、greeting（问候）。
                用户输入：%s
                请只返回一个处理器类型，不要包含其他文本。
                """, userInput);

        // 调用大模型进行意图识别
        String intent = Objects.requireNonNull(openAiChatClient.prompt(intentPrompt)
                .call()
                .content()).trim();

        // 根据意图路由到对应的处理器
        if ("rag".equals(intent)) {
            // 触发 RAG 检索并注入上下文
            String ragContext = GsonUtil.toJsonStr(embeddingModel.embedForResponse(List.of(userInput)));
            String enrichedInput = "结合知识库回答：" + ragContext + " 问题：" + userInput;
            return openAiChatClient.prompt(enrichedInput).call().content();
        } else if ("general".equals(intent)) {
            // 直接调用大模型生成回复
            return openAiChatClient.prompt(userInput).call().content();
        } else {
            // 处理问候等意图
            return "您好，欢迎咨询！";
        }
    }

}

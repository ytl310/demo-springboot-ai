package com.example.demo.config;

import com.example.demo.handler.ChineseSummaryMetadataEnricher;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * @author ytl
 * @date 2026/6/30 16:33
 * @description TODO
 */
@Configuration
public class AiModelConfig {

    //    @Bean
//    public ChatClient chatClient(ChatClient.Builder builder) {
//        return builder.build();
//    }  //修改为如下一个模型一个模型配置


    @Bean
    public ChatClient.Builder chatClientBuilder(@Qualifier("openAiChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }

    /**
     * @author ytl
     * @date 2026/7/8 0:04
     * @param []
     * @return org.springframework.ai.chat.memory.ChatMemory
     * @description 默认基于内存
     **/
    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder().maxMessages(20).build();
    }

//    // 为 DeepSeek 模型创建 ChatClient
//    @Bean("deepSeekChatClient")
//    public ChatClient deepSeekChatClient(DeepSeekChatModel deepSeekChatModel) {
//        return ChatClient.builder(deepSeekChatModel).build();
//    }

    // 为 OpenAI 模型创建 ChatClient
//    @Bean("openAiChatClient")
//    public ChatClient openAiChatClient(ChatClient.Builder chatClientBuilder) {
//        return chatClientBuilder.build();
//    }



    @Bean("openAiChatClient")
    public ChatClient ragAdvisor(@Qualifier("myVectorStore") VectorStore vectorStore,
                              ChatClient.Builder chatClientBuilder, ChatMemory chatMemory) {
        RetrievalAugmentationAdvisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        //topK 表示从向量数据库中返回最相似的前K条数据。
                        .topK(3)
                        // 相似度阈值，用于控制相似度过滤，值越高结果越严格
                        .similarityThreshold(0.50)
                        // 你的向量数据库实例
                        .vectorStore(vectorStore)
                        .build())
//        去掉 queryTransformers，直接使用原始查询检索，不需要额外的 LLM 调用，除非检索不正确，
//        需要进行优化重写，但是得保证重写的也得传conversationId，不然报conversationId不能为null
//                .queryTransformers(RewriteQueryTransformer.builder()
//                        .chatClientBuilder(chatClientBuilder)
//                        .build())

                .build();

        // 2. 定义对话记忆顾问（基于会话ID）
        Advisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        // 注意：这里不在这里设userId，留着在外面动态传参

        // 3. 返回一个调用链：先调记忆（恢复上下文），再调RAG（检索增强）
        // 通常将记忆放在最外层
        return chatClientBuilder
                .defaultAdvisors(memoryAdvisor,ragAdvisor)
                .build();
    }

    @Bean("chineseSummaryMetadataEnricher")
    public ChineseSummaryMetadataEnricher chineseSummaryMetadataEnricher(@Qualifier("openAiChatModel") ChatModel chatModel) {
        return new ChineseSummaryMetadataEnricher(
                chatModel,
                List.of(
                        ChineseSummaryMetadataEnricher.SummaryType.CURRENT,
                        ChineseSummaryMetadataEnricher.SummaryType.NEXT
                )
        );
    }

}

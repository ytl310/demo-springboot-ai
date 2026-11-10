package com.example.demo.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.RedisClient;

/**
 * @author ytl
 * @date 2026/6/30 17:03
 * @description TODO
 */
@Configuration
public class VectorStoreConfig {

    /**
     * @author ytl
     * @date 2026/6/30 18:02
     * @param [embeddingModel, redisClient]
     * @return org.springframework.ai.vectorstore.VectorStore
     * @description 配置文件配置的redis，没有自动加载RedisClient，所以需要自己另外加载，
     * 但spring.ai的RedisClient底层是jedis客户端，
     * 所以上面使用Lettuce连接池管理封装为RedisClient找不到，
     * 本来想使用jedispool连接池的，但是好像没有转为RedisClient，所以暂时只能使用单jedis
     *
     *
     * 其中EmbeddingModel要明确注入@Qualifier("ollamaEmbeddingModel")为ollama的嵌入模型，
     * 不然默认走的是openai的嵌入模型
     *
     * EmbeddingModel采用Redis缓存，但好像没有自带过期时间设置
     **/
    @Bean("myVectorStore")
    public VectorStore vectorStore(@Qualifier("openAiEmbeddingModel") EmbeddingModel embeddingModel, RedisClient redisClient) {
        return RedisVectorStore.builder(redisClient, embeddingModel)
                .indexName("custom-index")        // 自定义索引名称
                .prefix("custom-prefix:")        // 自定义 Redis 键前缀
                // 先禁用，等确认连接成功后再启用,不然我的连不上，一直404
                .initializeSchema(true)          // 是否自动初始化索引 Schema
                // .metadataFields(...)          // 可选：配置元数据字段（如 metadataField.tag("field")）
                // .batchingStrategy(...)        // 可选：配置写入分批策略
                .build();
    }


}
//package com.example.demo;
//
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.ai.document.Document;
//import org.springframework.ai.vectorstore.VectorStore;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.ApplicationArguments;
//import org.springframework.boot.ApplicationRunner;
//import org.springframework.boot.context.event.ApplicationReadyEvent;
//import org.springframework.context.event.EventListener;
//import org.springframework.stereotype.Component;
//
//import java.util.List;
//import java.util.Map;
//
///**
// * @author ytl
// * @date 2026/7/7 18:37
// * @description 可以使用@EventListener(ApplicationReadyEvent.class)，也可以实现ApplicationRunner等
// */
//@Component
//@Slf4j
//public class InitDataRunner {
//
//    @Autowired
//    private VectorStore vectorStore;
//
//    @EventListener(ApplicationReadyEvent.class)
//    public void initVectorData() {
//        try {
//            // 检查是否已有数据
//            List<Document> existingDocs = vectorStore.similaritySearch("测试");
//            if (!existingDocs.isEmpty()) {
//                log.info("✅ 向量库已有数据，跳过初始化");
//                return;
//            }
//
//            log.info("🔄 开始初始化向量数据...");
//
//            // 加载您的数据
//            List<Document> documents = loadDocuments();
//            vectorStore.add(documents);
//
//            log.info("✅ 成功加载 {} 条数据到向量库", documents.size());
//
//        } catch (Exception e) {
//            log.error("❌ 初始化向量数据失败", e);
//            throw new RuntimeException("初始化向量数据失败");
//        }
//    }
//
//    private List<Document> loadDocuments() {
//        // 从文件、数据库等加载数据
//        return List.of(
//                new Document("Spring AI 是一个用于构建AI应用的框架",
//                        Map.of("source", "docs", "type", "framework")),
//                new Document("Redis Vector Store 支持向量相似性搜索",
//                        Map.of("source", "docs", "type", "database"))
//        );
//    }
//}

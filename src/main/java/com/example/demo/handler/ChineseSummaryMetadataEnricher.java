package com.example.demo.handler;


import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.document.MetadataMode;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author ytl
 * @date 2026/7/2 19:55
 * @description Spring AI 内置的 SummaryMetadataEnricher 使用英文提示词 ，生成的摘要对中文用户不友好。
 * 为此，我们自定义 ChineseSummaryMetadataEnricher，使用中文提示词模板生成高质量摘要
 */
//@Component //因为在其他地方引用是需要有参构造器的，如果@Component，
// 要注入有参构造器还需要在ChineseSummaryMetadataEnricher本类建立构造器
// ⭐ 新增：告诉 Spring 用这个构造器注入
//    @Autowired
//    public ChineseSummaryMetadataEnricher(ChatModel chatModel) {
//        this(
//            chatModel,
//            List.of(SummaryType.CURRENT, SummaryType.NEXT),  // 默认值
//            CHINESE_SUMMARY_TEMPLATE,
//            MetadataMode.ALL
//        );
//    }
// 然后其他注入，
// 所以使用@Configuration启动时就设置好有参构造器直接使用好
public class ChineseSummaryMetadataEnricher implements DocumentTransformer {

    private static final String SECTION_SUMMARY_METADATA_KEY = "section_summary";
    private static final String NEXT_SECTION_SUMMARY_METADATA_KEY = "next_section_summary";
    private static final String PREV_SECTION_SUMMARY_METADATA_KEY = "prev_section_summary";
    private static final String CONTEXT_STR_PLACEHOLDER = "context_str";

    // 中文提示词模板
    public static final String CHINESE_SUMMARY_TEMPLATE = """
        请对以下中文文本进行专业摘要，要求：

        1. 使用简洁明了的中文
        2. 总结主要内容，不超过100字
        3. 保持专业性和准确性

        文本内容：
        {context_str}

        中文摘要：
        """;


    private final ChatModel chatModel;
    private final List<SummaryType> summaryTypes;
    private final MetadataMode metadataMode;
    private final String summaryTemplate;

    public enum SummaryType {
        CURRENT, PREVIOUS, NEXT
    }

    public ChineseSummaryMetadataEnricher(ChatModel chatModel, List<SummaryType> summaryTypes) {
        this(chatModel, summaryTypes, CHINESE_SUMMARY_TEMPLATE, MetadataMode.ALL);
    }

    public ChineseSummaryMetadataEnricher(ChatModel chatModel, List<SummaryType> summaryTypes,
                                          String summaryTemplate, MetadataMode metadataMode) {
        Assert.notNull(chatModel, "ChatModel must not be null");
        Assert.hasText(summaryTemplate, "Summary template must not be empty");

        this.chatModel = chatModel;
        this.summaryTypes = CollectionUtils.isEmpty(summaryTypes) ?
                List.of(SummaryType.CURRENT,SummaryType.NEXT) : summaryTypes;
        this.metadataMode = metadataMode;
        this.summaryTemplate = summaryTemplate;
    }

    @Override
    public List<Document> apply(List<Document> documents) {
        List<String> documentSummaries = new ArrayList<>();

        for (Document document : documents) {
            var documentContext = document.getFormattedContent(this.metadataMode);
            Prompt prompt = new PromptTemplate(this.summaryTemplate)
                    .create(Map.of(CONTEXT_STR_PLACEHOLDER, documentContext));

            String summary = chatModel.call(prompt).getResult().getOutput().getText();
            documentSummaries.add(summary.trim());
        }

        // 将摘要写入元数据
        for (int i = 0; i < documentSummaries.size(); i++) {
            Map<String, Object> summaryMetadata = getSummaryMetadata(i, documentSummaries);
            documents.get(i).getMetadata().putAll(summaryMetadata);
        }

        return documents;
    }

    private Map<String, Object> getSummaryMetadata(int i, List<String> summaries) {
        Map<String, Object> metadata = new HashMap<>();
        if (i > 0 && summaryTypes.contains(SummaryType.PREVIOUS)) {
            metadata.put(PREV_SECTION_SUMMARY_METADATA_KEY, summaries.get(i - 1));
        }
        if (i < summaries.size() - 1 && summaryTypes.contains(SummaryType.NEXT)) {
            metadata.put(NEXT_SECTION_SUMMARY_METADATA_KEY, summaries.get(i + 1));
        }
        if (summaryTypes.contains(SummaryType.CURRENT)) {
            metadata.put(SECTION_SUMMARY_METADATA_KEY, summaries.get(i));
        }
        return metadata;
    }
}


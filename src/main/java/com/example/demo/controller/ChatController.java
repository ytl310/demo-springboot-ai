package com.example.demo.controller;

import com.example.demo.vo.MovieInfoVO;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatClient openAiChatClient;

    public ChatController(@Qualifier("openAiChatClient") ChatClient openAiChatClient) {
        this.openAiChatClient = openAiChatClient;
    }

    @PostMapping("/chat")
    public String chat(@RequestParam String message) {
        return openAiChatClient.prompt()
                .user(message)
                .call()
                .content();
    }

    @PostMapping("/chatStream")
    public Flux<String> chatStream(@RequestParam String message) {
        return openAiChatClient.prompt()
                .user(message)
                .stream()
                .content();
    }



    @PostMapping("/getInfo")
    public List<MovieInfoVO>  getInfo(@RequestParam String type) {
        return openAiChatClient.prompt("推荐3部"+type+"类型的经典电影，包含名称、类型、年份、评分和推荐理由")
                .call()
                .entity(new ParameterizedTypeReference<>() {
                });
    }

//    @PostMapping("/getInfo")
//    public MovieList getInfo(@RequestParam String type) {
//        return openAiChatClient.prompt()
//                .call()
//                .entity(MovieList.class);
//    }
//
//    public record MovieList(List<MovieInfoVO> movieInfoVOList){
//
//    }


}

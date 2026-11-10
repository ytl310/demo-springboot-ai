package com.example.demo.handler;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.stereotype.Component;

/**
 * @author ytl
 * @date 2026/7/14 17:59
 * @description TODO
 */
@Component
@Slf4j
public class RAGObservationHandle implements ObservationHandler<VectorStoreObservationContext> {


    public void onStop(VectorStoreObservationContext context) {
        log.info("------------------- rag查询到的数据: start ------------------------");
        context.getQueryResponse().forEach(result -> {
            // 这里截取一下，避免打印太多数据
            log.info(" rag查询到的数据: {}", result.getText().substring(0, 30));
        });
        log.info("------------------- rag查询到的数据: end ------------------------");
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof VectorStoreObservationContext;
    }

}
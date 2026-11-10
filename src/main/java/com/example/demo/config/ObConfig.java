package com.example.demo.config;

import com.example.demo.handler.RAGObservationHandle;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author ytl
 * @date 2026/7/14 18:03
 * @description TODO
 */
@Configuration
@Slf4j
public class ObConfig {

    @Autowired
    private RAGObservationHandle ragObservationHandle;

    @Bean
    public ObservationRegistry observationRegistry() {
        // 创建 ObservationRegistry
        ObservationRegistry registry = ObservationRegistry.create();
        // 注册 ObservationHandler
        registry.observationConfig().observationHandler(ragObservationHandle);

        return registry;
    }
}

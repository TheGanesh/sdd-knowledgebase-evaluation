package com.ganesh.catalog.rag;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class RagMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagMcpApplication.class, args);
    }

    /** Local ONNX embedding model (all-MiniLM-L6-v2). Downloads the model once; no API key. */
    @Bean
    EmbeddingModel embeddingModel() throws Exception {
        TransformersEmbeddingModel model = new TransformersEmbeddingModel();
        model.afterPropertiesSet();
        return model;
    }

    @Bean
    ToolCallbackProvider ragToolCallbacks(RagTools ragTools) {
        return MethodToolCallbackProvider.builder().toolObjects(ragTools).build();
    }
}

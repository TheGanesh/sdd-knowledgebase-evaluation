package com.ganesh.catalog.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class CatalogMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogMcpApplication.class, args);
    }

    /** Registers every @Tool method on CatalogTools as an MCP tool. */
    @Bean
    public ToolCallbackProvider catalogToolCallbacks(CatalogTools catalogTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(catalogTools)
                .build();
    }
}

package com.ganesh.catalog.rag;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/** The single Graph-RAG tool the agent calls during feature-to-story (KB-only). */
@Component
public class RagTools {

    private final RagStore store;

    public RagTools(RagStore store) {
        this.store = store;
    }

    @Tool(name = "rag_search",
            description = "Semantic retrieval over the service catalog using local embeddings, with "
                    + "1-hop dependency-graph expansion. Pass a feature description or question; returns "
                    + "the most relevant knowledge-base chunks (services, API operations, Kafka topics) "
                    + "plus their graph neighbours (callers, topic consumers, etc.). Use this to find which "
                    + "services/APIs a feature touches when you only have the knowledge base, not the code.")
    public Map<String, Object> ragSearch(
            @ToolParam(description = "feature description or natural-language question") String query,
            @ToolParam(description = "how many top chunks to retrieve (default 5)", required = false) Integer k) {
        return store.ragSearch(query, k == null ? 5 : k);
    }
}

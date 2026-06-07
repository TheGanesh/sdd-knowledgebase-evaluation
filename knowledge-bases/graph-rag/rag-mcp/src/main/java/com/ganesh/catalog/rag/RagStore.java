package com.ganesh.catalog.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Graph-RAG knowledge base: a local-embedding vector store over the catalog chunks, plus a
 * 1-hop expansion step that walks the dependency graph from each retrieved chunk. This is the
 * difference from plain vector RAG — retrieval finds the semantically closest nodes, then the
 * graph pulls in their structural neighbours (callers, the topic's consumers, etc.).
 */
@Component
public class RagStore {

    private final ObjectMapper json = new ObjectMapper();
    private final EmbeddingModel embeddingModel;
    private final Path catalogRoot;
    private final Path indexFile;
    private final boolean rebuild;

    private SimpleVectorStore vectorStore;
    private JsonNode graph;
    private JsonNode reverse;
    private final Map<String, Document> byId = new LinkedHashMap<>();

    public RagStore(EmbeddingModel embeddingModel,
                    @Value("${rag.catalog-root}") String catalogRoot,
                    @Value("${rag.index-file}") String indexFile,
                    @Value("${rag.rebuild:false}") boolean rebuild) {
        this.embeddingModel = embeddingModel;
        this.catalogRoot = Path.of(catalogRoot).toAbsolutePath().normalize();
        this.indexFile = Path.of(indexFile).toAbsolutePath().normalize();
        this.rebuild = rebuild;
    }

    @PostConstruct
    void init() throws IOException {
        graph = json.readTree(catalogRoot.resolve("generated/graph.json").toFile());
        reverse = json.readTree(catalogRoot.resolve("generated/reverse-index.json").toFile());

        // Chunks are cheap to rebuild (string-building) and give us the id->text map for expansion.
        List<Document> chunks = new CatalogChunker().chunk(catalogRoot);
        for (Document d : chunks) {
            byId.put((String) d.getMetadata().get("id"), d);
        }

        vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        if (!rebuild && Files.isRegularFile(indexFile)) {
            vectorStore.load(indexFile.toFile());           // reuse embeddings (no re-embed)
        } else {
            vectorStore.add(chunks);                        // embed (the expensive step)
            Files.createDirectories(indexFile.getParent());
            vectorStore.save(indexFile.toFile());
        }
    }

    /** Semantic retrieval + 1-hop graph expansion. */
    public Map<String, Object> ragSearch(String query, int k) {
        List<Document> hits = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(k).build());

        Set<String> retrievedIds = new LinkedHashSet<>();
        List<Map<String, Object>> retrieved = new ArrayList<>();
        for (Document d : hits) {
            String id = (String) d.getMetadata().get("id");
            retrievedIds.add(id);
            retrieved.add(chunkView(d, d.getScore()));
        }

        // Expand: union of 1-hop neighbours of every hit, minus what we already retrieved.
        Set<String> neighbourIds = new LinkedHashSet<>();
        for (String id : retrievedIds) {
            neighbourIds.addAll(neighbours(id));
        }
        neighbourIds.removeAll(retrievedIds);
        List<Map<String, Object>> expanded = new ArrayList<>();
        for (String id : neighbourIds) {
            Document d = byId.get(id);
            if (d != null) {
                expanded.add(chunkView(d, null));
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("query", query);
        out.put("k", k);
        out.put("retrieved", retrieved);
        out.put("graphExpanded", expanded);
        return out;
    }

    // ---- 1-hop graph neighbours of a node id ----

    private Set<String> neighbours(String id) {
        Set<String> out = new LinkedHashSet<>();
        if (id.startsWith("service:")) {
            String svc = id.substring("service:".length());
            JsonNode node = graph.path(svc);
            for (JsonNode o : node.path("outboundApis")) {
                out.add("service:" + o.path("targetService").asText());
            }
            for (JsonNode t : node.path("topicsProduced")) {
                addTopicPeers(out, t.asText(), "consumers");
            }
            for (JsonNode t : node.path("topicsConsumed")) {
                addTopicPeers(out, t.asText(), "producers");
            }
            for (JsonNode d : reverse.path("services").path(svc).path("dependents")) {
                out.add("service:" + d.asText());
            }
        } else if (id.startsWith("api:")) {
            JsonNode entry = reverse.path("apis").path(id);
            out.add("service:" + entry.path("provider").asText());
            for (JsonNode c : entry.path("consumers")) {
                out.add("service:" + c.asText());
            }
        } else if (id.startsWith("topic:")) {
            String topic = id.substring("topic:".length());
            addTopicPeers(out, topic, "producers");
            addTopicPeers(out, topic, "consumers");
        }
        out.remove(id);
        return out;
    }

    private void addTopicPeers(Set<String> out, String topic, String role) {
        for (JsonNode s : reverse.path("topics").path(topic).path(role)) {
            out.add("service:" + s.asText());
        }
    }

    private Map<String, Object> chunkView(Document d, Double score) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getMetadata().get("id"));
        m.put("type", d.getMetadata().get("type"));
        if (score != null) {
            m.put("score", Math.round(score * 1000.0) / 1000.0);
        }
        m.put("text", d.getText());
        return m;
    }
}

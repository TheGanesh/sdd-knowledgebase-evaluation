# Graph-RAG knowledge base

Semantic retrieval over the catalog **plus** a graph step — the industry-standard "GraphRAG"
shape. Pure vector RAG finds the chunks that *read* like the query; the graph step then pulls in
each hit's structural neighbours (the caller, the topic's consumers) that vector similarity alone
would miss.

## How it works
1. **Chunk** the catalog into retrievable documents — one per service, per inbound API operation,
   and per Kafka topic (`CatalogChunker`).
2. **Embed** locally with all-MiniLM-L6-v2 via Spring AI `TransformersEmbeddingModel` — **no API
   key**, no cost. Stored in a `SimpleVectorStore` persisted to `index/vectorstore.json`.
3. **Serve** `rag_search(query, k)` as an MCP tool (`rag-mcp`, SSE :3003): vector top-k, then
   1-hop expansion along `graph.json` / `reverse-index.json`.

```
rag_search("reserve stock endpoint", k=1)
  retrieved:     api:inventory-service/POST /api/inventory/reserve
  graphExpanded: service:inventory-service, service:order-service   ← the graph step
```

## Build & run
```bash
cd rag-mcp
CATALOG_ROOT=../../graph-mcp/catalog RAG_INDEX=../index/vectorstore.json RAG_REBUILD=true \
  mvn spring-boot:run        # builds the index (downloads the model once) and serves :3003
```
Or use the `/build-kb-graph-rag` workflow. Register in your MCP config:
`"Graph RAG": { "transport": "sse", "url": "http://localhost:3003/sse" }`.

> Embeddings are local for reproducibility. Swap in OpenAI/Voyage by replacing the `EmbeddingModel`
> bean in `RagMcpApplication` — one line.

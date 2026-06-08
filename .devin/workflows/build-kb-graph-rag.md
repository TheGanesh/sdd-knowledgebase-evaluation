---
auto_execution_mode: 0
description: Build the Graph-RAG knowledge base — index the catalog with local embeddings and start the rag-mcp server (:3003).
---

# /build-kb-graph-rag

Build approach 2 (semantic retrieval + graph expansion). Local embeddings (all-MiniLM) — no API key.

1. **Build the vector index and start `rag-mcp`** (SSE :3003). First run downloads the embedding
   model (~once) and writes `knowledge-bases/graph-rag/index/vectorstore.json`.
   ```bash
   ROOT="$(pwd)"
   CAT="$ROOT/knowledge-bases/graph-mcp/catalog"
   IDX="$ROOT/knowledge-bases/graph-rag/index/vectorstore.json"
   lsof -nP -iTCP:3003 -sTCP:LISTEN -t | xargs -r kill
   (cd "$ROOT/knowledge-bases/graph-rag/rag-mcp" \
     && CATALOG_ROOT="$CAT" RAG_INDEX="$IDX" RAG_REBUILD=true nohup mvn -q spring-boot:run > /tmp/rag-mcp.log 2>&1 &)
   ```

2. **Wait for health** and a smoke query.
   ```bash
   for i in $(seq 1 40); do curl -s -m 2 -N http://localhost:3003/sse | grep -q event:endpoint && break; sleep 2; done
   echo "rag-mcp up on :3003"
   ```

> Subsequent starts: drop `RAG_REBUILD=true` to reuse the saved index (no re-embedding).
> Register: `"Graph RAG": { "transport": "sse", "url": "http://localhost:3003/sse" }`.

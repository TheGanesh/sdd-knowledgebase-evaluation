---
auto_execution_mode: 0
description: Build all three knowledge bases (markdown, graph-mcp, graph-rag) from the catalog and start both MCP servers.
---

# /build-all-kbs

Rebuild everything in dependency order, then start the two MCP servers.

1. **Build the catalog graph** (the shared source for all three KBs).
   // turbo
   ```bash
   ROOT="$(pwd)"; CAT="$ROOT/knowledge-bases/graph-mcp/catalog"
   (cd "$ROOT/knowledge-bases/graph-mcp/builder/graph-builder" && mvn -q exec:java -Dexec.args="$CAT")
   ```

2. **Render the markdown KB.**
   // turbo
   ```bash
   ROOT="$(pwd)"; CAT="$ROOT/knowledge-bases/graph-mcp/catalog"
   (cd "$ROOT/knowledge-bases/graph-mcp/builder/graph-builder" \
     && mvn -q exec:java -Dexec.mainClass=com.ganesh.catalog.graph.MarkdownGenerator \
        -Dexec.args="$CAT $ROOT/knowledge-bases/markdown/kb")
   ```

3. **Start `catalog-mcp` (:3002) and `rag-mcp` (:3003).**
   ```bash
   ROOT="$(pwd)"; CAT="$ROOT/knowledge-bases/graph-mcp/catalog"
   lsof -nP -iTCP:3002 -sTCP:LISTEN -t | xargs -r kill
   (cd "$ROOT/knowledge-bases/graph-mcp/mcp-server" && CATALOG_ROOT="$CAT" nohup mvn -q spring-boot:run > /tmp/catalog-mcp.log 2>&1 &)
   lsof -nP -iTCP:3003 -sTCP:LISTEN -t | xargs -r kill
   (cd "$ROOT/knowledge-bases/graph-rag/rag-mcp" \
     && CATALOG_ROOT="$CAT" RAG_INDEX="$ROOT/knowledge-bases/graph-rag/index/vectorstore.json" \
        nohup mvn -q spring-boot:run > /tmp/rag-mcp.log 2>&1 &)
   sleep 10
   curl -s -m3 -N http://localhost:3002/sse | head -1; curl -s -m3 -N http://localhost:3003/sse | head -1
   ```

Now run `/feature-to-story <ID> --kb=...` for each approach, then `/judge-knowledgebases all`.

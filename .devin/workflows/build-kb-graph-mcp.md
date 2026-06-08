---
auto_execution_mode: 0
description: Build the Graph+MCP knowledge base — (re)generate the dependency graph from the catalog and start the catalog-mcp server (:3002).
---

# /build-kb-graph-mcp

Build approach 3 (deterministic graph traversal). Workspace root is the repo root.

1. **(Optional) Re-extract descriptors from code.** Only if a service's API/Kafka/DB surface
   changed. This is the *only* step that reads `microservices/`, and it runs offline.
   ```bash
   ROOT="$(pwd)"; EX="$ROOT/knowledge-bases/graph-mcp/builder/extractor"
   for svc in $(ls "$ROOT/microservices" | grep -v pom.xml); do
     (cd "$EX" && mvn -q exec:java -Dexec.args="$ROOT/microservices/$svc $ROOT/knowledge-bases/graph-mcp/catalog/services/$svc")
   done
   ```

2. **Build the graph + reverse index** (validates every descriptor against the schema).
   // turbo
   ```bash
   ROOT="$(pwd)"; CAT="$ROOT/knowledge-bases/graph-mcp/catalog"
   (cd "$ROOT/knowledge-bases/graph-mcp/builder/graph-builder" && mvn -q exec:java -Dexec.args="$CAT")
   ```

3. **Start `catalog-mcp`** (SSE :3002), reading the freshly built graph.
   ```bash
   ROOT="$(pwd)"; CAT="$ROOT/knowledge-bases/graph-mcp/catalog"
   lsof -nP -iTCP:3002 -sTCP:LISTEN -t | xargs -r kill
   (cd "$ROOT/knowledge-bases/graph-mcp/mcp-server" && CATALOG_ROOT="$CAT" nohup mvn -q spring-boot:run > /tmp/catalog-mcp.log 2>&1 &)
   sleep 8 && curl -s -m 3 -N http://localhost:3002/sse | head -3
   ```

Register once in your MCP config: `"Graph MCP": { "transport": "sse", "url": "http://localhost:3002/sse" }`.

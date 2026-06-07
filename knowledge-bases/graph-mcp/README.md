# Graph + MCP knowledge base

The structured catalog and the **deterministic** access mode: the agent calls graph tools that
traverse the real dependency graph, so impact analysis is *computed*, not retrieved or guessed.

## Layout
- `builder/extractor` — scans `microservices/` (JavaParser) → per-service `catalog-info.yaml`.
- `builder/graph-builder` — validates descriptors against the schema, emits the graph + reverse index,
  and renders the markdown view (`MarkdownGenerator`).
- `catalog/` — the canonical KB: `schema/`, per-service descriptors (`services/`), and `generated/`
  (`graph.json`, `reverse-index.json`, `catalog-summary.yaml`). **This is the source the other two
  KBs are built from.**
- `mcp-server/` — `catalog-mcp` (SSE :3002) exposing `list_services`, `search_services`, `get_service`,
  `get_api`, `get_dependents`, `get_dependencies`, `impact_report`.

## Build & run
```bash
# build the graph from the descriptors
cd builder/graph-builder && mvn -q exec:java -Dexec.args="$(cd ../../catalog && pwd)"
# serve it
cd ../../mcp-server && CATALOG_ROOT=$(cd ../catalog && pwd) mvn spring-boot:run   # :3002
```
Or use `/build-kb-graph-mcp`. Register: `"Graph MCP": { "transport": "sse", "url": "http://localhost:3002/sse" }`.

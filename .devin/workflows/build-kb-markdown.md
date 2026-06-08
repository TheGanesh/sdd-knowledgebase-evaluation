---
auto_execution_mode: 0
description: Build the Markdown knowledge base — render the human-readable prose KB from the catalog into knowledge-bases/markdown/kb.
---

# /build-kb-markdown

Build approach 1 (prose in context). Generated from the same catalog as the other KBs, so it never
drifts. No server — the feature-to-story runner reads the files directly.

1. **Render the markdown KB** straight into `knowledge-bases/markdown/kb`.
   // turbo
   ```bash
   ROOT="$(pwd)"; CAT="$ROOT/knowledge-bases/graph-mcp/catalog"
   (cd "$ROOT/knowledge-bases/graph-mcp/builder/graph-builder" \
     && mvn -q exec:java -Dexec.mainClass=com.ganesh.catalog.graph.MarkdownGenerator \
        -Dexec.args="$CAT $ROOT/knowledge-bases/markdown/kb")
   ```

2. **Confirm** the index + per-service pages exist.
   ```bash
   ls "$(pwd)/knowledge-bases/markdown/kb"
   ```

> If the catalog graph hasn't been built yet, run `/build-kb-graph-mcp` step 2 first (the generator
> reads the descriptors under `graph-mcp/catalog`).

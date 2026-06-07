# Markdown knowledge base

The baseline: human-readable prose the agent reads directly in its context window — "give the LLM
the READMEs." Zero infrastructure, instantly readable, and how most teams document services today.

## Contents
- `kb/index.md` — one-line-per-service index with links.
- `kb/<service>.md` — per-service page: inbound APIs, outbound calls, Kafka topics, datastores, and
  the human narrative.

These are **generated** from the same canonical catalog (`graph-mcp/catalog`) via the
`MarkdownGenerator`, so they never drift from the contracts — but at query time the agent only sees
prose and must *infer* cross-service impact by reading, with no way to compute it.

## Build
```bash
cd ../graph-mcp/builder/graph-builder
mvn -q exec:java -Dexec.mainClass=com.ganesh.catalog.graph.MarkdownGenerator \
  -Dexec.args="$(cd ../../catalog && pwd)"
# copy/point the generated view into markdown/kb (the /build-kb-markdown workflow does this)
```
Or use `/build-kb-markdown`. No server — the feature-to-story runner is pointed at `kb/`.

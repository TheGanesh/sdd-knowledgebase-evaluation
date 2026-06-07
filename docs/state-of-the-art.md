# State of the art: giving an LLM cross-service knowledge

How do you let an LLM reason about ~40 microservices — "what does this feature touch?" — without
pasting all the code into the prompt? This is a *knowledge-base-for-LLM* problem, and the field has
converged on a handful of patterns. Here's the landscape, and why we benchmark three of them.

## The landscape

| Pattern | Idea | Strength | Weakness |
|---|---|---|---|
| **Full-context / prose** | Put the docs (READMEs) in the prompt | Zero infra; human-readable | Doesn't scale to 40 services; impact is *inferred*, not computed |
| **Vector RAG** | Chunk + embed the KB; retrieve top-k by similarity | Scales; finds semantically related text | Retrieval misses; no notion of structure/relationships |
| **Knowledge-graph RAG (GraphRAG)** | RAG + a graph: expand retrieved nodes along edges / community summaries | Recovers relationships vector search alone misses | Over-expansion noise; still probabilistic |
| **Agentic tool-use / MCP** | Expose deterministic queries (graph traversal) as tools the model calls | Exact, repeatable impact analysis | Needs a built graph + a running server |
| **Hybrid** | RAG for recall + tools for precision | Best of both | More moving parts |
| **Fine-tuning / CPT** | Bake the KB into weights | No retrieval at inference | Stale immediately; expensive; opaque |
| **Long-context stuffing** | Rely on a huge context window | Simple | Cost, "lost in the middle", still no structure |

## Why these three

We benchmark the three that represent the meaningfully different *access modes* a team would
realistically choose today, all built from the **same** structured catalog so the comparison is
about the access mode, not the data:

1. **Markdown KB → full-context / prose.** The baseline almost everyone starts with: per-service
   READMEs the agent reads. Represents "we already write docs; let the LLM read them."
2. **Graph-RAG → knowledge-graph RAG.** The industry-standard retrieval answer: embed the catalog,
   retrieve by similarity, and expand 1 hop along the dependency graph (the "graph" in GraphRAG).
   Local embeddings (all-MiniLM) keep it reproducible and key-free.
3. **Graph + MCP → agentic tool-use.** The deterministic answer: the model calls graph tools
   (`get_dependents`, `impact_report`) that traverse the real dependency graph. Impact is computed.

We deliberately exclude fine-tuning (stale, opaque, overkill for an evolving catalog) and pure
long-context stuffing (doesn't generalise past a demo, and adds no structure). Hybrid is the natural
*next* step once you've measured the three primitives — which is what this repo does.

## What we measure, and how

The one question that matters for feature→story: **does the approach correctly identify every
affected service / API / Kafka topic, and avoid inventing ones that aren't?**

We judge this **pairwise** — the three specs are compared against the **feature story** (the ground
truth), with the `llm-pr-judge` bias controls (position, verbosity, charity). This deliberately
avoids a hand-labelled answer key, which would bias the result toward whoever wrote it. The
framework keeps **reference mode** open: once an independently-authored `gold-impact.json` is
trusted, the same criteria score each approach by distance from gold. Verdict →
[`../llm-as-judge/results/comparison-report.md`](../llm-as-judge/results/comparison-report.md).

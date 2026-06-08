---
trigger: always_on
description: During feature→story generation the agent must use only the knowledge base, never the microservices source code.
---

# KB isolation — the fair-test rule

The whole experiment measures how well each **knowledge base** lets an agent reason about
cross-service impact. Reading the source code would defeat that.

During `/feature-to-story` (and any spec generation under evaluation):

- **Never read anything under `microservices/`.** No `cat`, `grep`, `find`, `open`, or tool call
  that touches that folder. Treat it as off-limits.
- **Use only the selected KB:** the markdown files under `knowledge-bases/markdown/kb`, **or** the
  Graph-RAG tool `rag_search` (:3003), **or** the Graph-MCP tools (:3002) — whichever the run specifies.
- **If the KB cannot answer, say so in the spec.** "The markdown KB does not make the
  `order.created` consumer explicit" is a valid, valuable finding — far better than silently opening
  the code. The judge rewards honest gaps over cheated completeness.

The microservices source is used **only** offline: by the KB builders to generate the KBs, and by
the evaluator to label `gold-impact.json`. Never at query time.

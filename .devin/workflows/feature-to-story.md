---
auto_execution_mode: 0
description: Generate a feature→story spec from ONE knowledge base only (no code access). Produces llm-as-judge/specs/<ID>/<kb>.md for the comparison.
---

# /feature-to-story

Turn a story into a spec using **only** the chosen knowledge base. This is the unit under test —
so the `kb-isolation` rule applies: **never read `microservices/` source.**

Usage: `/feature-to-story <ID> --kb=markdown|graph-rag|graph-mcp`

## Steps

1. **Read the story:** `llm-as-judge/tasks/<ID>/story.md`. Note its acceptance criteria.

2. **Load ONLY the selected KB** — nothing else:
   - `--kb=markdown` → read `knowledge-bases/markdown/kb/index.md` and the relevant
     `knowledge-bases/markdown/kb/<service>.md` pages. Prose only; you must *infer* impact.
   - `--kb=graph-rag` → call the **Graph RAG** MCP tool `rag_search(query, k)` (server :3003) with
     the feature description; use the retrieved chunks + `graphExpanded` neighbours.
   - `--kb=graph-mcp` → call the **Graph MCP** tools (server :3002): `search_services`,
     `impact_report`, `get_dependents`, `get_api`, `get_dependencies`.

3. **Determine cross-service impact from that KB alone.** Which services, which API operations
   (with request/response), which outbound calls, which DB/Cache/Kafka. If the KB cannot tell you,
   say so in the spec — do **not** open the code (that's a finding the judge rewards).

4. **Write the spec** using `feature-to-story/spec-template.md` →
   `llm-as-judge/specs/<ID>/<kb>.md`. Fill section 2 (Cross-Service Impact) precisely; the judge scores it.

## Guardrail
- Honour `.windsurf/rules/kb-isolation.md`: no `cat`/`grep`/open of anything under `microservices/`.
- State how you determined impact (read prose / `rag_search` / `get_dependents`) in the confidence note.

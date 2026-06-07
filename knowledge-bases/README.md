# Knowledge bases — three approaches, one source

All three KBs are built from the **same canonical catalog** (the structured descriptors under
`graph-mcp/catalog`, derived offline from `microservices/`). That is deliberate: the experiment
isolates the **access mode**, not the underlying data. Same facts, three ways to hand them to an LLM.

| Folder | Approach | How the agent reads it | Server |
|---|---|---|---|
| `markdown/` | **Markdown KB** | Prose docs placed in the prompt context ("read the READMEs") | none |
| `graph-rag/` | **Graph-RAG** | Semantic vector retrieval (local embeddings) + 1-hop graph expansion, via `rag_search` | `rag-mcp` :3003 |
| `graph-mcp/` | **Graph + MCP** | Deterministic graph traversal via tools (`get_dependents`, `impact_report`) | `catalog-mcp` :3002 |

Build any one with its Windsurf workflow (`/build-kb-markdown`, `/build-kb-graph-rag`,
`/build-kb-graph-mcp`) or all three with `/build-all-kbs`. The evaluation (`/judge-knowledgebases`)
compares how accurately each lets the agent answer "what does this feature touch?".

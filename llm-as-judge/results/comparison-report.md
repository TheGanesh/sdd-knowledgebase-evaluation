# KB-Approach Comparison Report: Markdown vs Graph-RAG vs Graph-MCP

**Story:** `llm-as-judge/tasks/KB-201/story.md` (representative; behaviour generalised across KB-201..206)
**Approaches:** A = `markdown` · B = `graph-rag` · C = `graph-mcp`
**Mode:** pairwise (the feature story is the ground truth — no golden dataset)
**Judge:** `/judge-knowledgebases` per `.devin/rules/judge-rubric.md`

## Overall Result

**Winner: C — Graph + MCP**
**Weighted scores:** Graph-MCP **0.97** · Graph-RAG **0.74** · Markdown **0.67** (out of 1.0)
**Margin (1st − 2nd):** 0.23 · **Recommend human review:** No

### Summary
Graph + MCP wins decisively: it *computes* impact by traversing the dependency graph, so it leads on
completeness, contract precision, and determinism, and never over-claims. Markdown is the most
readable and never invents impact, but misses non-local effects (consumers of changed events, callers
of changed APIs). Graph-RAG sits in the middle — graph expansion recovers the edges Markdown misses,
but it over-expands on single-service changes and its contracts are paraphrased.

## Per-Group Scores

| Group | Weight | Winner | Markdown (A) | Graph-RAG (B) | Graph-MCP (C) |
|---|---|---|---:|---:|---:|
| Impact completeness (G1) | 0.35 | C | 0.65 | 0.90 | **1.00** |
| Scope precision (G2) | 0.20 | A & C | **1.00** | 0.70 | **1.00** |
| Contract precision (G3) | 0.15 | C | 0.40 | 0.60 | **1.00** |
| Determinism (G4) | 0.20 | C | 0.40 | 0.60 | **1.00** |
| Spec usability (G5) | 0.10 | A | **1.00** | 0.70 | 0.70 |
| **Overall** | 1.00 | **C** | **0.67** | **0.74** | **0.97** |

## Top 3 Deciding Evidence

1. **Non-local impact (G1).** On a change to the `order.created` event, **Graph-MCP** names
   `inventory-service` as the consumer via `get_dependents`, and **Graph-RAG** surfaces it from the
   topic chunk — but **Markdown** only *hedges* it ("may need to handle the new field — please
   confirm"), which the rubric scores as not-claimed.
2. **Over-expansion (G2).** On single-service tasks (KB-202, KB-205), **Graph-RAG**'s 1-hop expansion
   drags in the neighbouring service (a false positive), while **Markdown** and **Graph-MCP** stay
   correctly scoped.
3. **Contracts (G3).** **Graph-MCP** returns exact schemas (`CreateOrderRequest → OrderResponse`,
   `ReserveRequest/Response`) from `get_api`; **Graph-RAG** paraphrases them from chunk text;
   **Markdown** gives prose only.

## Per-Criterion Breakdown

### G1 — Impact completeness (0.35) → winner C
| Approach | Score | Evidence |
|---|---:|---|
| Graph-MCP | 1.00 | `get_dependents('topic:order.created') → [inventory-service]` — flags the consumer (AC3). |
| Graph-RAG | 0.90 | order.created chunk lists producer + consumer; surfaces inventory-service. |
| Markdown | 0.65 | hedges the consumer change → counts as not-claimed. |

### G2 — Scope precision (0.20) → tie A & C
| Approach | Score | Evidence |
|---|---:|---|
| Markdown | 1.00 | never claims an unaffected service (KB-202, KB-205 stay in-service). |
| Graph-MCP | 1.00 | no dependents returned for isolated changes — correctly scoped. |
| Graph-RAG | 0.70 | graph expansion pulls the neighbour into single-service changes (false positive). |

### G3 — Contract precision (0.15) → winner C
| Approach | Score | Evidence |
|---|---:|---|
| Graph-MCP | 1.00 | exact schemas + examples from `get_api`. |
| Graph-RAG | 0.60 | schemas paraphrased from chunk text. |
| Markdown | 0.40 | request/response in prose only. |

### G4 — Determinism (0.20) → winner C
| Approach | Score | Evidence |
|---|---:|---|
| Graph-MCP | 1.00 | reverse-graph lookup — identical every run. |
| Graph-RAG | 0.60 | depends on `k` / phrasing. |
| Markdown | 0.40 | depends which pages the reader opens. |

### G5 — Spec usability (0.10) → winner A
| Approach | Score | Evidence |
|---|---:|---|
| Markdown | 1.00 | plain prose, instantly readable, zero infra. |
| Graph-RAG | 0.70 | structured but needs a running server. |
| Graph-MCP | 0.70 | structured/precise but needs a running server. |

## Position-Bias Control
Rankings were stable when the three specs were re-presented in a different order — the evidence is
concrete (named dependencies, schema presence), not a length/style preference. `order_sensitive_criteria` is empty.

## Risks (regardless of winner)
- **Markdown:** low recall on non-local impact (misses topic consumers / API callers); the gap widens at 40 services.
- **Graph-RAG:** over-expansion false positives on local changes; approximate contracts; non-deterministic retrieval.
- **Graph-MCP:** needs a built graph + a running MCP server — the operational cost of the deterministic win.

---
*Pairwise today. When an independently-authored `tasks/<ID>/gold-impact.json` is trusted, re-run in
**reference mode** for distance-from-gold scoring — same criteria, same report, different anchor.*

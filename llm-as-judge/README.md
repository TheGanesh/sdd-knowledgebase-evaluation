# LLM-as-judge

Compares the three KB approaches' feature→story specs and emits an evidence-backed verdict.
Modelled on `llm-pr-judge`: a rubric-as-rule (`.devin/rules/judge-rubric.md`), a workflow
(`/judge-knowledgebases`), and a JSON + markdown report.

## Mode

- **Pairwise (default, today):** the **feature story is the ground truth**; the three specs are
  ranked against it and each other with bias controls (position, verbosity, charity). No golden
  dataset — which avoids answer-key bias.
- **Reference (future):** score each spec by distance from an independently-authored
  `tasks/<ID>/gold-impact.json`. Same criteria, different anchor.

## Layout

```
tasks/<ID>/
  story.md            the feature — ground truth for pairwise
  gold-impact.json    seed for the future reference anchor (illustrative; replace with
                      independently-authored gold before trusting reference mode)
specs/<ID>/
  {markdown,graph-rag,graph-mcp}.md   each approach's spec (from /feature-to-story, KB-only)
results/
  comparison-report.md     the pairwise verdict (human)
  comparison-report.json   conforms to docs/judge-output-schema.json (machine)
```

## Run

```
/judge-knowledgebases all
```

Current verdict: **Graph+MCP 0.97 · Graph-RAG 0.74 · Markdown 0.67** (margin 0.23) — see
`results/comparison-report.md`.

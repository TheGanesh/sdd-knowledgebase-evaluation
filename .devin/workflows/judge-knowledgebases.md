---
description: LLM-as-judge — pairwise compare the three KB approaches' specs against the feature story and emit an evidence-backed verdict (JSON + markdown report).
---

# /judge-knowledgebases

Compare the three knowledge-base approaches by how well each spec captures the feature's true
cross-service impact. **Pairwise** today (the story is the ground truth — no golden dataset needed);
the same workflow runs **reference mode** once `tasks/<ID>/gold-impact.json` is trusted. Mirrors
`/judge-prs` from `llm-pr-judge`.

Usage: `/judge-knowledgebases [<ID> | all]`   (default: all)

## Steps

1. **Load the rubric** (`.windsurf/rules/judge-rubric.md` — mirrored to `.devin/rules/`) and the
   output contract (`docs/judge-output-schema.json`). Follow them exactly.

2. **Read the story** (`llm-as-judge/tasks/<ID>/story.md`) — the ground truth. List what it *implies*:
   which services change, which APIs, and which Kafka events (and therefore which producers/consumers
   are dragged in). You will judge each spec against this, not against your own opinion.

3. **Gather the three specs:** `llm-as-judge/specs/<ID>/{markdown,graph-rag,graph-mcp}.md`. Treat
   them as Option 1/2/3.

4. **Apply the rubric criteria (G1–G5).** For each criterion, write the evidence (quote each spec's
   claim/omission and the story item it maps to), then score each approach in `[0,1]` with a confidence.
   A *hedged* claim counts as not-claimed.

5. **Position-bias control.** Re-do step 4 with the three specs presented in a different order. Keep a
   criterion's ranking only if it is stable; otherwise add its id to `order_sensitive_criteria`.

6. **Aggregate.** Per-group score per approach → weighted overall (rubric weights). `margin =
   top − second`. If `margin < 0.10`, set `recommend_human_review: true`.

7. **Emit the verdict** (both files):
   - `llm-as-judge/results/comparison-report.json` — conforms to `docs/judge-output-schema.json`
     (`mode: "pairwise"`, options a/b/c, overall winner/margin/summary, groups with per-approach
     scores + per-criterion evidence, `order_sensitive_criteria`, `risks`).
   - `llm-as-judge/results/comparison-report.md` — Overall result + margin; a per-group score table
     (one column per approach); the top deciding evidence; a per-criterion breakdown; and the bias note.

8. **Self-check:** every judgement cites a story item or a spec line; no criterion decided on length;
   hedged claims were treated as misses; the verdict is fair to each approach's real strengths
   (e.g. Markdown's readability) even where it loses overall.

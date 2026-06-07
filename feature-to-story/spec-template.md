# Feature → Story spec (KB-only)

> Produced by the agent **from a knowledge base only** — no `microservices/` source access.
> The Cross-Service Impact section is what the evaluation scores against ground truth.

## Story
- **ID:** `<ID>`
- **Title:** <one line>
- **KB used:** <markdown | graph-rag | graph-mcp>

## 1. Outcomes
- AC1: ...

## 2. Cross-Service Impact  (the graded section)
- **Affected services:** `<svc-a>`, `<svc-b>`

| Service | API operation (method + path) | Request → Response | Outbound calls | DB / Cache / Kafka |
|---|---|---|---|---|
| `<svc>` | `POST /api/...` | `<Req>` → `<Resp>` | `<target>#op` | db:`<table>` · cache:`<region>` · topic:`<name>` |

- **Events touched:** `topic:<name>` — producers `[...]`, consumers `[...]`
- **Confidence note:** how the impact was determined (read prose / rag_search / get_dependents).

## 3. Task Breakdown
| # | File/Area | Change | Reason |
|---|---|---|---|

## 4. Verification
- ...

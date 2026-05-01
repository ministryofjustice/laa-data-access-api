# MakeDecision Performance Investigation

## SQL comparison: `ApplicationService.makeDecision` vs `MakeDecisionUseCase`

| | Old `ApplicationService` | New `MakeDecisionUseCase` |
|---|---|---|
| Application SELECT | 1 | 1 |
| Proceedings SELECT | 1 (by IDs) | 1 (by app_id + JOIN to merits_decisions) |
| INSERT merits_decisions | 3 | 3 |
| INSERT decisions | 1 | 1 |
| UPDATE applications | 1 (all columns — no `@DynamicUpdate`) | 1 (4 changed columns — `@DynamicUpdate`) |
| INSERT linked_merits_decisions | 3 | 0 (schema change: FK now on proceedings) |
| UPDATE proceedings | 0 | 3 (new — sets `merits_decision_id`) |
| SELECT certificates | 1 | 1 |
| INSERT domain_events | 1 | 1 |
| **Total queries** | **12** | **12** |

## Why the new path is slower under concurrent load

Query count is identical, so the slowdown is not about more SQL. The causes are:

1. **Connection pool exhaustion** — HikariCP defaults to 10 max connections. With 10 concurrent
   create→get→makeDecision futures each holding a connection for the full `@Transactional` duration,
   the pool is fully saturated. Any additional DB call queues.

2. **New `UPDATE proceedings` writes** — the old path never touched the `proceedings` table during
   `makeDecision` (it only inserted into the `linked_merits_decisions` join table). The new schema
   stores `merits_decision_id` as a FK on `proceedings`, so each decision requires 3 extra row-level
   writes. Under 10 concurrent futures that is 30 extra write locks acquired simultaneously, adding
   to the total time each connection is held.

3. **Mapping round-trip marks proceedings dirty** — `ProceedingGatewayMapper.applyToEntity` was
   unconditionally calling `setProceedingContent(...)` and `setLead(...)` with new object references
   even though `makeDecision` never changes those fields. Hibernate's dirty check for
   `@JdbcTypeCode(SqlTypes.JSON)` serialises both maps to compare them, marking the entity dirty and
   forcing a full-column `UPDATE proceedings` (no `@DynamicUpdate` on `ProceedingEntityV2`).

## Fixes applied

| Fix | File |
|---|---|
| Removed `setProceedingContent` / `setLead` from `applyToEntity` — only set `meritsDecision` | `ProceedingGatewayMapper` |
| Added `@DynamicUpdate` to `ProceedingEntityV2` — only changed columns written to SQL | `ProceedingEntityV2` |
| Increased HikariCP `maximum-pool-size` to 20, added `minimum-idle` and timeouts | `application.yml` |
| Added `hikaricp.connections.acquire` histogram to metrics | `application.yml` |

To confirm pool pressure is the bottleneck, hit `/actuator/metrics/hikaricp.connections.pending`
during a concurrent test run — if it is ever > 0, connections are queuing.


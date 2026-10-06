# Endpoint Errors: Linked Applications

Linked-application command failures use the service's standard Problem Detail response. A linked
group or version conflict is HTTP 409; a missing application route is HTTP 404; malformed or
missing required version input is HTTP 400.

## `POST /api/v0/applications/{id}/link`

| Status | Detail |
|---|---|
| 409 | `Application <sourceId> already belongs to a different linked group` |
| 409 | `Linked group of application <sourceId> has changed since version <n>` |
| 409 | `Application <targetId> is in a linked group; linkedGroupVersion is required` |
| 409 | `Application <targetId> is no longer in a linked group; re-read before linking` |
| 409 | `Application <targetId> is no longer in the linked group identified by linkedGroupVersion; re-read before retrying` |
| 400 | `linkedGroupVersion: must be a valid linked group version token` |

When the target is already linked, supply the target group's `linkedGroupVersion` as last read. A
version is not supplied for a standalone target. Linking an application that is already in the same
group remains an idempotent success.

## `POST /api/v0/applications/{id}/make-lead`

| Status | Detail |
|---|---|
| 400 | Missing or invalid `linkedGroupVersion` |
| 404 | `No application group route found for application <id>` |
| 409 | `Application <id> is not in a linked group` |
| 409 | `Linked group of application <id> has changed since version <n>` |
| 409 | `Application <id> is no longer in the linked group identified by linkedGroupVersion; re-read before retrying` |

Making the current lead lead again is an idempotent success only when the token names the
application's current group, even when the supplied version is stale.

## `POST /api/v0/applications/{id}/unlink`

| Status | Detail |
|---|---|
| 400 | Missing or invalid `linkedGroupVersion` |
| 404 | `No application group route found for application <id>` |
| 409 | `Application <id> is not in a linked group` |
| 409 | `Application <id> is the lead of its linked group; change the lead before removing it` |
| 409 | `Linked group of application <id> has changed since version <n>` |
| 409 | `Application <id> is no longer in the linked group identified by linkedGroupVersion; re-read before retrying` |

Because query projections are asynchronous, re-fetch the application after a 409 and use the latest
`linkedGroupVersion` for a retry.

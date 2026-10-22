# Endpoint Errors: Linked Applications and Assignment

Linked-application command failures use the service's standard Problem Detail response. A linked
group or version conflict is HTTP 409; a missing application route is HTTP 404; malformed or
missing required version input is HTTP 400.

## `POST /api/v0/applications/{id}/link`

| Status | Detail |
|---|---|
| 409 | `Application <sourceId> already belongs to a different linked group` |
| 409 | `Target linked group's version does not match the supplied linkedGroupVersion; re-read before retrying` |
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
| 409 | `Linked group of application <id> does not match the supplied linkedGroupVersion; re-read before retrying` |
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
| 409 | `Linked group of application <id> does not match the supplied linkedGroupVersion; re-read before retrying` |
| 409 | `Application <id> is no longer in the linked group identified by linkedGroupVersion; re-read before retrying` |

Because query projections are asynchronous, re-fetch the application after a 409 and use the latest
`linkedGroupVersion` for a retry.

## `POST /api/v0/prior-authorities`

| Status | Detail |
|---|---|
| 400 | `Prior Authority applications can only be created against a lead or standalone application` |

Prior Authority draft creation first validates that the application is granted. It then uses the
current write-side linked-group state: standalone applications and the current lead are eligible;
associated applications are rejected. A concurrent membership or lead change is serialized with
draft creation by the group-route locks.

## `POST /api/v0/work-list/{itemId}/assign`

| Status | Detail |
|---|---|
| 400 | `expectedAssignmentVersion: must be a valid assignment version token` |
| 404 | `No work-item route found for <itemId>` |
| 409 | `Work item <itemId> cannot be updated: the assignment version is stale` |
| 409 | `Work item <itemId> cannot be updated: it is already assigned` |
| 409 | `Work item <itemId> cannot be updated: it is no longer a member of its linked group` |
| 409 | `Work item <itemId> cannot be updated: its linked group membership changed; re-read before retrying` |

## `POST /api/v0/work-list/{itemId}/unassign`

| Status | Detail |
|---|---|
| 400 | `expectedAssignmentVersion: must be a valid assignment version token` |
| 404 | `No work-item route found for <itemId>` |
| 409 | `Work item <itemId> cannot be updated: the assignment version is stale` |
| 409 | `Work item <itemId> cannot be updated: it is already unassigned` |
| 409 | `Work item <itemId> cannot be updated: it is no longer a member of its linked group` |
| 409 | `Work item <itemId> cannot be updated: its linked group membership changed; re-read before retrying` |

### Assignment tokens

`assignmentVersion` is an opaque token naming who owns the item's assignment (the item, or the
linked group that owns it) and its version. Pass it back unchanged as
`expectedAssignmentVersion`; do not parse it. For an active member of a linked group, the work list
shows `assignmentBoundaryType = LINKED_GROUP` and the group's token; assigning or unassigning any
member changes the whole group. When an application joins or leaves a group, tokens read before
the change name the old owner and receive 409 `the assignment version is stale`; re-read the work
list.

## `PATCH /api/v0/applications/{id}/decision` (linked groups)

| Status | Detail |
|---|---|
| 409 | `Work item <id> cannot be updated: it is unassigned` |
| 409 | `Work item <id> cannot be updated: the supplied caseworker is not its current assignee` |
| 409 | `Work item <id> cannot be updated: it is no longer a member of its linked group` |
| 409 | `Work item <id> cannot be updated: its linked-group assignee was not validated` |
| 409 | `Work item <id> cannot be updated: its linked group membership changed; re-read before retrying` |

## `PATCH /api/v0/applications/{id}/auto-grant-outcome`

| Status | Detail |
|---|---|
| 409 | `Linked group membership of application <id> changed; retry the request` |

The auto-grant-outcome endpoint continues to accept its existing service-role caller as well as a
named caseworker; linked-group assignment coordination does not change that authorization.

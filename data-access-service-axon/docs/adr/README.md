# Architecture Decision Records

Architecture decision records (ADRs) capture significant design choices in the Axon module, the
context in which they were made, and the conditions that should cause them to be reconsidered.

| ADR | Status | Decision |
|---|---|---|
| [0002](0002-separate-sensitive-data-from-domain-events.md) | Accepted | Store sensitive application data as immutable versions referenced by thin events |
| [0003](0003-define-application-behaviour-after-retention-deletion.md) | Proposed | Give applications an explicit retention-deleted state after sensitive data is deleted |
| [0004](0004-proxy-prior-authority-document-downloads.md) | Proposed | Proxy Prior Authority document downloads without exposing signed storage URLs |
| [0005](0005-linked-group-assignment-stays-per-application.md) | Accepted | Keep assignment on each application and synchronise active linked-group members |
| [0006](0006-every-event-names-its-genesis.md) | Proposed | Every domain event identifies the genesis IDs used to build read-only lifecycle streams |
| [0007](0007-event-content-is-stored-beside-its-event.md) | Proposed | Keep only schema-listed fields on stored events and store erasable content beside them |

ADRs describe why a decision was made. The developer guides describe how the resulting
implementation works today.

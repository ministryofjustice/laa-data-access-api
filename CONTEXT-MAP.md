# Context Map

## Contexts

- [Framework vocabulary](./laa-data-access-api-workbench/CONTEXT.md) — Axon Framework and CQRS/ES patterns used across the stack; framework-level ADRs in [`laa-data-access-api-workbench/docs/adr/`](./laa-data-access-api-workbench/docs/adr/)
- [LAA Data Access API](./docs/CONTEXT.md) — domain language specific to this service; project ADRs in [`docs/adr/`](./docs/adr/)

## Relationships

- The project context builds on the framework vocabulary: terms defined in the framework context (aggregate, command, event, projection) are used without redefinition in the project context.
- Project ADRs may reference framework ADRs where a domain decision is grounded in an Axon/CQRS pattern.

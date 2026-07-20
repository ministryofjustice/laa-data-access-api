# Sequence Diagrams

Mermaid sequence diagrams for the `data-access-service-axon` module.

For the rules and design choices behind these message flows, read
[Linked applications](../linked-applications.md). For the wider component layout, start with the
[developer guide](../README.md).

| File | Description |
|---|---|
| [01-standalone-application-creation.md](01-standalone-application-creation.md) | Creating an application with no lead link |
| [02-linked-application-creation.md](02-linked-application-creation.md) | Creating an application that joins an existing group |
| [03-second-member-joins-group.md](03-second-member-joins-group.md) | A second associated application joining an already-formed group |
| [04-sensitive-data-change.md](04-sensitive-data-change.md) | Appending an immutable sensitive-data version and emitting a thin event |

Diagrams are written in [Mermaid](https://mermaid.js.org/) and render natively in GitHub, IntelliJ, and VS Code with the Mermaid plugin.

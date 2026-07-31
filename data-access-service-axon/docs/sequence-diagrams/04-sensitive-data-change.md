# Sensitive-Data Change and Thin Event

Decision, assignment, unassignment, and note commands use the same central pattern: load the
current immutable payload, append a complete next version, and emit a thin event that references
it. The example below uses a decision because it also demonstrates optimistic locking.

```mermaid
sequenceDiagram
    actor Client
    participant Controller as ApplicationCommandController
    participant CmdGateway as CommandGateway
    participant Aggregate as ApplicationAggregate
    participant DataStore as ApplicationDataStore
    participant DataDB as application_data
    participant EventStore as Axon event store
    participant Projection as ApplicationProjection<br/>(tracking)
    participant CurrentDB as application_current_state

    Client->>Controller: PATCH /applications/{id}/decision<br/>{ applicationVersion, decision details }
    Controller->>CmdGateway: sendAndWait(MakeApplicationDecisionCommand)
    CmdGateway->>Aggregate: load event stream and handle command

    alt Application stream does not exist
        CmdGateway-->>Controller: AggregateNotFoundException
        Controller-->>Client: 404 Not Found
    else Application exists
        Aggregate->>Aggregate: compare expected applicationVersion
        alt Version is stale
            Aggregate-->>Controller: ApplicationVersionConflictException
            Controller-->>Client: 409 Conflict
        else Version matches
            Aggregate->>DataStore: get(applicationId, current data version)
            DataStore->>DataDB: SELECT immutable payload
            DataDB-->>DataStore: complete current payload
            DataStore-->>Aggregate: payload
            Aggregate->>Aggregate: validate and create complete updated payload
            Aggregate->>DataStore: append(applicationId, next data version, payload)
            DataStore->>DataDB: INSERT immutable row
            Aggregate->>Aggregate: apply(ApplicationDecisionMadeEvent<br/>applicationVersion + 1,<br/>applicationDataVersion + 1)
            Aggregate->>EventStore: commit thin event in command transaction
            CmdGateway-->>Controller: completed
            Controller-->>Client: 204 No Content
        end
    end

    EventStore-->>Projection: ApplicationDecisionMadeEvent
    Projection->>CurrentDB: update both version pointers
```

The data insert and event commit share command transaction management. If the data append fails, no
event is applied. If commit fails, the transaction rolls back the data insert.

Assignment and unassignment also advance `applicationVersion` and `applicationDataVersion`. Notes
advance only `applicationDataVersion`, because note creation is intentionally outside the decision
optimistic-lock contract.

Detailed query and history responses later join the thin state to `application_data` using
`(applicationId, applicationDataVersion)`. See
[Events and sensitive data](../events-and-sensitive-data.md).

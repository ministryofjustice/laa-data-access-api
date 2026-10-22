# Change a Linked Group's Lead

The command side locks the group's membership routes before dispatching the aggregate command.
Changing the lead changes no routes; its query projections update independently after the command
commits.

```mermaid
sequenceDiagram
    actor Client
    participant Controller as ApplicationCommandController
    participant UseCase as MakeApplicationLeadUseCase
    participant Coordinator as MakeApplicationLeadCoordinator
    participant Resolver as ApplicationGroupRouteResolver
    participant Dispatcher as RetryingCommandDispatcher
    participant GroupHandler as ChangeLinkedGroupLeadCommandHandler
    participant Aggregate as LinkedApplicationGroupAggregate
    participant EventStore as Event store
    participant AppProjection as ApplicationProjection<br/>(async)
    participant GroupProjection as LinkedApplicationGroupProjection<br/>(async)
    participant HistoryProjection as ApplicationHistoryProjection<br/>(async)
    participant ListProjection as ApplicationListIndexProjection<br/>(async)

    Client->>Controller: POST /api/v0/applications/{id}/make-lead<br/>linkedGroupVersion (token)
    Controller->>UseCase: execute(MakeApplicationLeadCommand)
    UseCase->>Coordinator: handle(command)
    Coordinator->>Resolver: resolveGroupForMutation(applicationId, expectedGroupId)
    Resolver->>Resolver: lock all group routes<br/>ordered by application ID
    Resolver->>Resolver: group ≠ expectedGroupId → 409
    Resolver-->>Coordinator: groupId
    Coordinator->>Dispatcher: dispatch(ChangeLinkedGroupLeadCommand)
    Dispatcher->>GroupHandler: handle(command)
    GroupHandler->>Aggregate: load (replay events)
    GroupHandler->>Aggregate: validate membership and expected version
    GroupHandler->>EventStore: append LinkedApplicationGroupLeadChangedEvent
    EventStore-->>Dispatcher: committed event
    GroupHandler-->>Dispatcher: success
    Dispatcher-->>Coordinator: success
    Coordinator-->>UseCase: success
    UseCase-->>Controller: success
    Controller-->>Client: 204 No Content

    par Independent query projections
        EventStore-->>AppProjection: LeadChanged
        AppProjection->>AppProjection: update lead references by linked_group_id
    and
        EventStore-->>GroupProjection: LeadChanged
        GroupProjection->>GroupProjection: update lead and group version
    and
        EventStore-->>HistoryProjection: LeadChanged
        HistoryProjection->>HistoryProjection: append history for both leads
    and
        EventStore-->>ListProjection: LeadChanged
        ListProjection->>ListProjection: update index lead references
    end

    Note over AppProjection,ListProjection: Processors are independent; query results can lag and may not advance together
    Note over Coordinator,EventStore: No route projection update is needed: membership routes do not store the lead
```

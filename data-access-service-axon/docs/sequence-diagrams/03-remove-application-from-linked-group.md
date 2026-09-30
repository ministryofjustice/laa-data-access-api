# Remove an Application from a Linked Group

The route rows for the complete group are locked before dispatch. A non-dissolving removal updates
the removed member's route synchronously with the event append. Removing one of two members emits a
single dissolution event; the synchronous route projection makes both routes standalone, while
read projections process the event asynchronously.

```mermaid
sequenceDiagram
    actor Client
    participant Controller as ApplicationCommandController
    participant UseCase as UnlinkApplicationUseCase
    participant Handler as UnlinkApplicationCommandHandler
    participant Resolver as ApplicationGroupRouteResolver
    participant Dispatcher as RetryingCommandDispatcher
    participant Aggregate as LinkedApplicationGroupAggregate
    participant EventStore as Event store
    participant RouteProjection as ApplicationGroupRouteProjection<br/>(sync)
    participant AppProjection as ApplicationProjection<br/>(async)
    participant GroupProjection as LinkedApplicationGroupProjection<br/>(async)
    participant HistoryProjection as ApplicationHistoryProjection<br/>(async)
    participant ListProjection as ApplicationListIndexProjection<br/>(async)

    Client->>Controller: POST /api/v0/applications/{id}/unlink<br/>linkedGroupVersion
    Controller->>UseCase: execute(UnlinkApplicationCommand)
    UseCase->>Handler: handle(command)
    Handler->>Resolver: resolveGroupForMutation(applicationId)
    Resolver->>Resolver: lock all group routes<br/>ordered by application ID
    Resolver-->>Handler: groupId
    Handler->>Dispatcher: dispatch(RemoveApplicationFromLinkedGroupCommand)
    Dispatcher->>Aggregate: handle(command)
    Aggregate->>Aggregate: validate member, lead rule, and expected version
    alt More than two members remain in the group
        Aggregate->>EventStore: append MemberRemovedFromGroupEvent
        EventStore->>RouteProjection: MemberRemovedFromGroupEvent
        RouteProjection->>RouteProjection: set removed member route to STANDALONE
    else Removal dissolves a two-member group
        Aggregate->>EventStore: append LinkedApplicationGroupDissolvedEvent
        EventStore->>RouteProjection: LinkedApplicationGroupDissolvedEvent
        RouteProjection->>RouteProjection: set both routes to STANDALONE
    end
    RouteProjection-->>EventStore: route update committed
    EventStore-->>Dispatcher: event and route update committed
    Dispatcher-->>Handler: success
    Handler-->>UseCase: success
    UseCase-->>Controller: success
    Controller-->>Client: 204 No Content

    par Independent query projections
        EventStore-->>AppProjection: MemberRemoved or Dissolved
        AppProjection->>AppProjection: clear linked_group_id for removed applications
    and
        EventStore-->>GroupProjection: MemberRemoved or Dissolved
        GroupProjection->>GroupProjection: update version or delete dissolved group row
    and
        EventStore-->>HistoryProjection: MemberRemoved or Dissolved
        HistoryProjection->>HistoryProjection: append left/dissolved history
    and
        EventStore-->>ListProjection: MemberRemoved or Dissolved
        ListProjection->>ListProjection: clear index lead references
    end

    Note over AppProjection,ListProjection: Independent processors can lag the synchronous route state and each other
```

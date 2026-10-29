# Linked-group assignment

Assignment is stored on each application. Coordinators lock the write-side routes before sending one
command; handlers load command-side aggregate state, decide and append all resulting events in one
batch.

## Link: add an active member to an assigned group (AC1)

```mermaid
sequenceDiagram
    actor Client
    participant Coordinator as LinkApplicationCoordinator
    participant Resolver as ApplicationGroupRouteResolver
    participant Handler as AddApplicationToLinkedGroupCommandHandler
    participant Group as LinkedApplicationGroupAggregate
    participant StateManager
    participant Decider as LinkedGroupAssignmentDecider
    participant Appender as EventAppender
    participant EventStore as Event store

    Client->>Coordinator: POST /api/v0/applications/{id}/link
    Coordinator->>Resolver: resolve(source, target)
    Resolver->>Resolver: lock routes in ascending application ID order
    Resolver->>Resolver: re-read membership, changed → 409
    Resolver-->>Coordinator: add to existing group
    Coordinator->>Handler: AddApplicationToLinkedGroupCommand
    Handler->>Group: load group through @InjectEntity
    Handler->>StateManager: load existing and joining applications
    StateManager-->>Handler: consistent application states
    Handler->>Decider: decide join (AC1)
    Decider-->>Handler: joining member WorkItemAssigned
    Handler->>Appender: append MemberAddedToGroupEvent and assignment events
    Appender->>EventStore: append one batch
    EventStore-->>Handler: committed
    Handler-->>Coordinator: success
    Coordinator-->>Client: 204 No Content
```

## Group assign

```mermaid
sequenceDiagram
    actor Client
    participant Coordinator as AssignWorkItemCoordinator
    participant Resolver as ApplicationGroupRouteResolver
    participant Guard as RouteLockGuardDispatchInterceptor
    participant Handler as LinkedGroupWorkItemAssignmentCommandHandler
    participant Group as LinkedApplicationGroupAggregate
    participant StateManager
    participant Decider as LinkedGroupAssignmentDecider
    participant EventStore as Event store

    Client->>Coordinator: POST /api/v0/work-list/{itemId}/assign
    Coordinator->>Resolver: lockForAssignment(itemId)
    Resolver->>Resolver: lock group routes in ascending application ID order
    Resolver->>Resolver: verify membership is unchanged
    Resolver-->>Coordinator: groupId
    Coordinator->>Guard: AssignLinkedGroupWorkItemCommand
    Guard->>Guard: verify RouteLockRegistry holds group routes
    Guard->>Handler: proceed
    Handler->>Group: load group through @InjectEntity
    Handler->>StateManager: load member applications
    StateManager-->>Handler: consistent application states
    Handler->>Decider: validate addressed item and decide group assignment
    Decider-->>Handler: WorkItemAssigned events for active members
    Handler->>EventStore: append all application events in one batch
    EventStore-->>Handler: committed
    Handler-->>Coordinator: success
    Coordinator-->>Client: 200 OK
```

## Readiness for a linked application

```mermaid
sequenceDiagram
    actor Client
    participant Coordinator as MarkApplicationReadyCoordinator
    participant Retry as RetryingCommandDispatcher
    participant Transaction as Fresh transaction
    participant Resolver as ApplicationGroupRouteResolver
    participant Guard as RouteLockGuardDispatchInterceptor
    participant Handler as MarkApplicationReadyCommandHandler
    participant StateManager
    participant Decider as LinkedGroupAssignmentDecider
    participant EventStore as Event store

    Client->>Coordinator: PATCH /api/v0/applications/{id}/auto-grant-outcome
    Coordinator->>Retry: dispatch complete lock-and-command attempt
    Retry->>Transaction: begin attempt
    Transaction->>Resolver: lockForAssignment(applicationId)
    Resolver-->>Transaction: groupId
    Transaction->>Guard: MarkApplicationReadyCommand
    Guard->>Guard: verify application and group locks
    Guard->>Handler: proceed
    Handler->>StateManager: load group and other active-member state
    StateManager-->>Handler: consistent state
    Handler->>Decider: decide readiness assignment
    Decider-->>Handler: optional WorkItemAssigned or WorkItemUnassigned
    Handler->>EventStore: append readiness and assignment events together
    EventStore-->>Handler: committed
    Handler-->>Transaction: result
    Transaction-->>Retry: result
    Retry-->>Coordinator: result
    Coordinator-->>Client: 204 No Content
    Note over Retry,Transaction: A retry starts a fresh transaction and reacquires the route locks
```

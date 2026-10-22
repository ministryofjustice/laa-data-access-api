# Linked-Group Assignment

These diagrams show how one aggregate owns a linked group's shared assignment, how ownership moves
when applications join or leave, and how readiness and decisions use the group assignment. Query
projections update asynchronously and each projection maintains its own assignment-tracking tables.

## 3.1 Assign a linked-group work item

```mermaid
sequenceDiagram
    actor Client
    participant Controller as WorkListCommandController
    participant Coordinator as AssignWorkItemCoordinator
    participant Resolver as WorkItemRouteResolver
    participant Dispatcher as RetryingCommandDispatcher
    participant GroupHandler as AssignLinkedGroupCommandHandler
    participant Aggregate as LinkedApplicationGroupAggregate
    participant EventStore as Event store
    participant Projections as Async work-list, application, list-index and history projections

    Client->>Controller: POST /api/v0/work-list/{itemId}/assign<br/>expectedAssignmentVersion token
    Controller->>Controller: decode opaque token (invalid → 400)
    Controller->>Coordinator: handle(AssignWorkItemCommand)
    Coordinator->>Resolver: lockRoute(itemId)
    Resolver->>Resolver: peek route, lock group routes<br/>ascending application ID
    Resolver->>Resolver: lock work-item route
    Resolver-->>Coordinator: LINKED_GROUP, groupId
    Coordinator->>Coordinator: token owner must equal groupId<br/>(otherwise stale → 409)
    Coordinator->>Dispatcher: dispatch(AssignLinkedGroupCommand)
    Dispatcher->>GroupHandler: handle(command)
    GroupHandler->>Aggregate: load (replay events)
    GroupHandler->>Aggregate: validate membership, version and unassigned state
    GroupHandler->>EventStore: append LinkedGroupAssignedEvent
    EventStore-->>Dispatcher: committed event
    Dispatcher-->>Coordinator: success
    Coordinator-->>Controller: success
    Controller-->>Client: 204 No Content

    par Independent projections
        EventStore-->>Projections: LinkedGroupAssignedEvent
        Projections->>Projections: update active members' assignments<br/>and append changed-member history rows
    end
    Note over Aggregate,EventStore: One group command and one event; no assignment fan-out to applications
    Note over Projections: Not-ready, auto-granted and decided members are not updated
```

## 3.3 Create a linked group and delegate assignment

```mermaid
sequenceDiagram
    actor Client
    participant Controller as ApplicationCommandController
    participant Coordinator as LinkApplicationCoordinator
    participant GroupResolver as ApplicationGroupRouteResolver
    participant WorkResolver as WorkItemRouteResolver
    participant Dispatcher as RetryingCommandDispatcher
    participant AppHandler as DelegateAssignmentToGroupCommandHandler
    participant GroupHandler as EstablishLinkedApplicationGroupCommandHandler
    participant AppAggregate as ApplicationAggregate
    participant GroupAggregate as LinkedApplicationGroupAggregate
    participant EventStore as Event store
    participant GroupRoutes as ApplicationGroupRouteProjection (sync)
    participant WorkRoutes as WorkItemRouteProjection (sync)
    participant Projections as Async query projections

    Client->>Controller: POST /api/v0/applications/{sourceId}/link
    Controller->>Coordinator: handle(LinkApplicationCommand)
    Coordinator->>GroupResolver: resolve(sourceId, targetId)
    GroupResolver->>GroupResolver: lock both application-group routes
    GroupResolver-->>Coordinator: CREATE_GROUP
    Coordinator->>WorkResolver: lockApplicationRoutes(sorted sourceId, targetId)
    loop each application (ascending ID)
        Coordinator->>Dispatcher: dispatch(DelegateAssignmentToGroupCommand)
        Dispatcher->>AppHandler: handle(command)
        AppHandler->>AppAggregate: load and delegate assignment
        AppHandler-->>Dispatcher: DelegatedAssignment
        Dispatcher-->>Coordinator: delegated caseworker or null
    end
    Coordinator->>Dispatcher: dispatch(EstablishLinkedApplicationGroupCommand)
    Dispatcher->>GroupHandler: handle(command)
    GroupHandler->>GroupAggregate: load / create group
    GroupHandler->>EventStore: append LinkedApplicationGroupCreatedEvent
    opt joining rules select a group caseworker
        GroupHandler->>EventStore: append LinkedGroupAssignedEvent
    end
    EventStore->>GroupRoutes: group creation event
    GroupRoutes->>GroupRoutes: set member routes to LINKED_GROUP
    EventStore->>WorkRoutes: group creation event
    WorkRoutes->>WorkRoutes: move existing work-item routes to LINKED_GROUP
    GroupRoutes-->>EventStore: route updates committed
    WorkRoutes-->>EventStore: route updates committed
    EventStore-->>Dispatcher: group events and route updates committed
    Dispatcher-->>Coordinator: success
    Coordinator-->>Controller: success
    Controller-->>Client: success

    par Independent query projections
        EventStore-->>Projections: group creation and assignment events
        Projections->>Projections: track group assignment and update active-member views
    end
    Note over Coordinator,EventStore: Delegation and group creation commit in the same transaction
```

## 3.11 A grouped application becomes ready

```mermaid
sequenceDiagram
    actor Client
    participant Controller as ApplicationCommandController
    participant UseCase as RecordAutoGrantOutcomeUseCase
    participant Coordinator as MarkApplicationReadyCoordinator
    participant Resolver as ApplicationGroupRouteResolver
    participant Dispatcher as RetryingCommandDispatcher
    participant ReadyHandler as MarkApplicationReadyCommandHandler
    participant AppAggregate as ApplicationAggregate
    participant GroupHandler as InheritLinkedGroupAssignmentCommandHandler
    participant GroupAggregate as LinkedApplicationGroupAggregate
    participant EventStore as Event store
    participant WorkRoutes as WorkItemRouteProjection (sync)
    participant Projections as Async query projections

    Client->>Controller: PATCH /api/v0/applications/{id}/auto-grant-outcome<br/>MANUAL
    Controller->>UseCase: recordReady(command)
    UseCase->>Coordinator: handle(MarkApplicationReadyCommand)
    Note over Client,Coordinator: Existing access permits a named API caseworker or LAA_DECIDE_AUTO_GRANT service role
    Coordinator->>Resolver: lockGroupRoutesForReadiness(applicationId)
    Resolver-->>Coordinator: groupId, or standalone
    Coordinator->>Dispatcher: dispatch(MarkApplicationReadyCommand)
    Dispatcher->>ReadyHandler: handle(command)
    ReadyHandler->>AppAggregate: load (replay events)
    ReadyHandler->>EventStore: append ApplicationReadyForManualAssessmentEvent
    EventStore->>WorkRoutes: ApplicationReadyForManualAssessmentEvent
    WorkRoutes->>WorkRoutes: create work-item route<br/>(LINKED_GROUP when grouped)
    WorkRoutes-->>EventStore: route committed
    Dispatcher-->>Coordinator: RECORDED
    opt grouped application and readiness recorded
        Coordinator->>Dispatcher: dispatch(InheritLinkedGroupAssignmentCommand)
        Dispatcher->>GroupHandler: handle(command)
        GroupHandler->>GroupAggregate: load linked group
        alt group has an assignee
            GroupHandler->>EventStore: append LinkedGroupMemberAssignmentInheritedEvent
        else group is unassigned
            GroupHandler->>GroupHandler: no assignment event
        end
    end
    EventStore-->>Projections: readiness and inherited-assignment events
    Projections->>Projections: add active work-list row and apply inherited assignment
    Coordinator-->>UseCase: success
    UseCase-->>Controller: success
    Controller-->>Client: success
```

## 3.6 An active member leaves a group

```mermaid
sequenceDiagram
    actor Client
    participant Controller as ApplicationCommandController
    participant Coordinator as UnlinkApplicationCoordinator
    participant GroupResolver as ApplicationGroupRouteResolver
    participant WorkResolver as WorkItemRouteResolver
    participant Dispatcher as RetryingCommandDispatcher
    participant GroupHandler as RemoveApplicationFromLinkedGroupCommandHandler
    participant GroupAggregate as LinkedApplicationGroupAggregate
    participant AppHandler as ReclaimAssignmentFromGroupCommandHandler
    participant AppAggregate as ApplicationAggregate
    participant EventStore as Event store
    participant GroupRoutes as ApplicationGroupRouteProjection (sync)
    participant WorkRoutes as WorkItemRouteProjection (sync)
    participant Projections as Async query projections

    Client->>Controller: POST /api/v0/applications/{id}/unlink<br/>linkedGroupVersion token
    Controller->>Coordinator: handle(UnlinkApplicationCommand)
    Coordinator->>GroupResolver: resolveGroupForMutation(id, expectedGroupId)
    GroupResolver->>GroupResolver: lock group routes<br/>ascending application ID
    GroupResolver-->>Coordinator: groupId
    Coordinator->>WorkResolver: lockGroupRoutes(groupId)
    Coordinator->>Dispatcher: dispatch(RemoveApplicationFromLinkedGroupCommand)
    Dispatcher->>GroupHandler: handle(command)
    GroupHandler->>GroupAggregate: load (replay events)
    GroupHandler->>GroupHandler: validate member, lead rule and group version
    GroupHandler->>EventStore: append MemberRemovedFromGroupEvent
    EventStore->>GroupRoutes: MemberRemovedFromGroupEvent
    GroupRoutes->>GroupRoutes: set removed member route to STANDALONE
    EventStore->>WorkRoutes: MemberRemovedFromGroupEvent
    WorkRoutes->>WorkRoutes: set member work-item route to STANDALONE
    GroupRoutes-->>EventStore: route updates committed
    WorkRoutes-->>EventStore: route updates committed
    Dispatcher-->>Coordinator: LinkedGroupRemovalResult
    loop each leaving application (ascending ID)
        Coordinator->>Dispatcher: dispatch(ReclaimAssignmentFromGroupCommand)
        Dispatcher->>AppHandler: handle(command)
        AppHandler->>AppAggregate: load application
        AppHandler->>EventStore: append ApplicationAssignmentReclaimedFromGroupEvent
    end
    EventStore-->>Projections: removal and reclaim events
    Projections->>Projections: clear group ownership and retain assignment<br/>with a new direct token for active members
    Coordinator-->>Controller: success
    Controller-->>Client: 204 No Content
    Note over Coordinator,EventStore: On dissolution, routes for both former members become STANDALONE and both assignments are reclaimed
```

## 3.13 Decide an application in a linked group

```mermaid
sequenceDiagram
    actor Client
    participant Controller as ApplicationCommandController
    participant UseCase as MakeApplicationDecisionUseCase
    participant Coordinator as MakeApplicationDecisionCoordinator
    participant Resolver as WorkItemRouteResolver
    participant Dispatcher as RetryingCommandDispatcher
    participant ValidateHandler as ValidateLinkedGroupAssigneeCommandHandler
    participant GroupAggregate as LinkedApplicationGroupAggregate
    participant DecisionHandler as MakeApplicationDecisionCommandHandler
    participant AppAggregate as ApplicationAggregate
    participant EventStore as Event store
    participant WorkRoutes as WorkItemRouteProjection (sync)
    participant Projections as Async query projections

    Client->>Controller: PATCH /api/v0/applications/{id}/decision
    Controller->>UseCase: execute(MakeApplicationDecisionCommand)
    UseCase->>Coordinator: handle(command)
    Coordinator->>Resolver: lockRouteIfPresent(applicationId)
    Resolver->>Resolver: lock group and work-item routes
    Resolver-->>Coordinator: LINKED_GROUP, groupId
    Coordinator->>Dispatcher: dispatch(ValidateLinkedGroupAssigneeCommand)
    Dispatcher->>ValidateHandler: handle(command)
    ValidateHandler->>GroupAggregate: load group and validate current assignee
    alt group is unassigned
        ValidateHandler-->>Dispatcher: 409 it is unassigned
        Dispatcher-->>Coordinator: conflict
        Coordinator-->>Controller: 409
    else supplied caseworker is not the group assignee
        ValidateHandler-->>Dispatcher: 409 not its current assignee
        Dispatcher-->>Coordinator: conflict
        Coordinator-->>Controller: 409
    else supplied caseworker is the current group assignee
        ValidateHandler-->>Dispatcher: validation succeeded (no event)
        Coordinator->>Dispatcher: dispatch decision with groupAssigneeValidatedFor
        Dispatcher->>DecisionHandler: handle(command)
        DecisionHandler->>AppAggregate: load and decide
        DecisionHandler->>EventStore: append ApplicationDecisionMadeEvent
        EventStore->>WorkRoutes: ApplicationDecisionMadeEvent
        WorkRoutes->>WorkRoutes: remove work-item route
        WorkRoutes-->>EventStore: route deletion committed
        EventStore-->>Projections: decision event
        Projections->>Projections: mark application inactive;<br/>retain caseworker shown at decision time
        Dispatcher-->>Coordinator: success
        Coordinator-->>Controller: success
        Controller-->>Client: success
    end
    Note over GroupAggregate,AppAggregate: Group validation and application decision run under the group's route locks
```

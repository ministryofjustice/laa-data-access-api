# Linked Application Creation (First Member Joining a New Group)

An application submitted with a `leadApplicationId` that has not yet formed a group.
The entire command chain — from `ApplicationCreatedEvent` through to group initialisation — runs
synchronously in a single Axon unit of work via the subscribing `ApplicationGroupEventRouter`.

```mermaid
sequenceDiagram
    actor Client
    participant Controller as ApplicationCommandController
    participant SubGateway as SubscriptionProjectionGateway
    participant CmdGateway as CommandGateway
    participant AssocAggregate as ApplicationAggregate<br/>(associated app)
    participant Factory as ApplicationCreationDetailsFactory
    participant Router as ApplicationGroupEventRouter<br/>(subscribing)
    participant LeadAggregate as ApplicationAggregate<br/>(lead app)
    participant GroupAggregate as LinkedApplicationGroupAggregate
    participant AppProjection as ApplicationProjection<br/>(tracking)
    participant GroupProjection as LinkedApplicationGroupProjection<br/>(tracking)

    Client->>Controller: POST /api/v0/applications<br/>{ allLinkedApplications: [{ leadApplicationId }] }

    Note over Controller,SubGateway: Subscription query opened BEFORE command dispatch
    Controller->>SubGateway: awaitProjection(FindApplicationByIdQuery)
    activate SubGateway

    Controller->>CmdGateway: sendAndWait(CreateApplicationCommand)
    CmdGateway->>AssocAggregate: handle(CreateApplicationCommand) [CREATE_IF_MISSING]
    AssocAggregate->>Factory: prepare(command)
    Factory-->>AssocAggregate: ApplicationCreationDetails (leadApplicationId set)
    Note over AssocAggregate: Guard: leadApplicationId ≠ self → OK
    AssocAggregate->>AssocAggregate: apply(ApplicationCreatedEvent)
    Note over AssocAggregate: isAssociatedMember = true

    AssocAggregate->>Router: on(ApplicationCreatedEvent) [same thread/UoW]
    Note over Router: leadApplicationId != null → proceeds

    opt Other associated apps referenced
        Router->>CmdGateway: sendAndWait(ValidateApplicationExistsCommand)
        CmdGateway->>AssocAggregate: handle(ValidateApplicationExistsCommand)<br/>[other associated aggregate]
        Note over AssocAggregate: applicationId == null → ResourceNotFoundException (404)
    end

    Router->>CmdGateway: sendAndWait(CreateLinkedApplicationGroupCommand → leadId)
    CmdGateway->>LeadAggregate: handle(CreateLinkedApplicationGroupCommand) [CREATE_IF_MISSING]
    Note over LeadAggregate: applicationId == null → ResourceNotFoundException (404)
    Note over LeadAggregate: isAssociatedMember == true → ApplicationGroupInvariantException (400)
    Note over LeadAggregate: Both guards pass here
    Note over LeadAggregate: groupId = nameUUIDFromBytes("linked-group:" + leadId)
    LeadAggregate->>LeadAggregate: apply(LinkedApplicationGroupRequested)

    LeadAggregate->>Router: on(LinkedApplicationGroupRequested) [same thread/UoW]
    Router->>CmdGateway: sendAndWait(InitialiseLinkedApplicationGroupCommand → groupId)
    CmdGateway->>GroupAggregate: handle(InitialiseLinkedApplicationGroupCommand) [CREATE_IF_MISSING]
    Note over GroupAggregate: First call — groupId == null
    Note over GroupAggregate: Validates lead is in member list
    GroupAggregate->>GroupAggregate: apply(LinkedApplicationGroupCreatedEvent)

    GroupAggregate-->>CmdGateway: (void)
    CmdGateway-->>Router: (void)
    Router-->>LeadAggregate: (void)
    LeadAggregate-->>CmdGateway: (void)
    CmdGateway-->>Router: (void)
    Router-->>AssocAggregate: (void)
    AssocAggregate-->>CmdGateway: applicationId
    CmdGateway-->>Controller: applicationId

    AppProjection->>AppProjection: on(ApplicationCreatedEvent) [async]
    AppProjection->>AppProjection: save ApplicationReadModel
    AppProjection->>SubGateway: emit update

    GroupProjection->>GroupProjection: on(LinkedApplicationGroupCreatedEvent) [async]
    GroupProjection->>GroupProjection: save LinkedApplicationGroupReadModel

    SubGateway-->>Controller: ApplicationReadModel received
    deactivate SubGateway
    Controller-->>Client: 201 Created + Location: /api/v0/applications/{id}
```

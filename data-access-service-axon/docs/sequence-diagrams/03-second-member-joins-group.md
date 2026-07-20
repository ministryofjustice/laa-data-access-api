# Second Member Joining an Existing Group

A second associated application is submitted referencing the same `leadApplicationId` as a
previously created application. Because `groupId` is deterministic
(`nameUUIDFromBytes("linked-group:" + leadId)`), the command routes to the same
`LinkedApplicationGroupAggregate`, which diffs the incoming member list and emits
`MemberAddedToGroupEvent` only for the new member.

```mermaid
sequenceDiagram
    actor Client
    participant Controller as ApplicationCommandController
    participant SubGateway as SubscriptionProjectionGateway
    participant CmdGateway as CommandGateway
    participant AssocAggregate as ApplicationAggregate<br/>(second associated app)
    participant Factory as ApplicationCreationDetailsFactory
    participant Router as ApplicationGroupEventRouter<br/>(subscribing)
    participant LeadAggregate as ApplicationAggregate<br/>(lead app)
    participant GroupAggregate as LinkedApplicationGroupAggregate<br/>(already exists)
    participant AppProjection as ApplicationProjection<br/>(tracking)
    participant GroupProjection as LinkedApplicationGroupProjection<br/>(tracking)

    Note over GroupAggregate: Pre-existing state:<br/>members = [leadId, firstMemberId]

    Client->>Controller: POST /api/v0/applications<br/>{ allLinkedApplications: [{ leadApplicationId }] }

    Controller->>SubGateway: awaitProjection(FindApplicationByIdQuery)
    activate SubGateway

    Controller->>CmdGateway: sendAndWait(CreateApplicationCommand)
    CmdGateway->>AssocAggregate: handle(CreateApplicationCommand) [CREATE_IF_MISSING]
    AssocAggregate->>Factory: prepare(command)
    Factory-->>AssocAggregate: ApplicationCreationDetails (leadApplicationId set)
    AssocAggregate->>AssocAggregate: apply(ApplicationCreatedEvent)
    Note over AssocAggregate: isAssociatedMember = true

    AssocAggregate->>Router: on(ApplicationCreatedEvent) [same thread/UoW]

    Router->>CmdGateway: sendAndWait(CreateLinkedApplicationGroupCommand → leadId)
    CmdGateway->>LeadAggregate: handle(CreateLinkedApplicationGroupCommand)
    Note over LeadAggregate: groupId = nameUUIDFromBytes("linked-group:" + leadId)<br/>← same groupId as before
    LeadAggregate->>LeadAggregate: apply(LinkedApplicationGroupRequested)

    LeadAggregate->>Router: on(LinkedApplicationGroupRequested) [same thread/UoW]
    Router->>CmdGateway: sendAndWait(InitialiseLinkedApplicationGroupCommand → groupId)
    CmdGateway->>GroupAggregate: handle(InitialiseLinkedApplicationGroupCommand) [CREATE_IF_MISSING]
    Note over GroupAggregate: groupId != null → group already exists
    Note over GroupAggregate: Diffs incoming members against current list:<br/>secondMemberId not present → emit MemberAddedToGroupEvent
    Note over GroupAggregate: leadId already present → no event
    GroupAggregate->>GroupAggregate: apply(MemberAddedToGroupEvent { memberId: secondMemberId })

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

    GroupProjection->>GroupProjection: on(MemberAddedToGroupEvent) [async]
    GroupProjection->>GroupProjection: append secondMemberId to memberIds<br/>save LinkedApplicationGroupReadModel

    SubGateway-->>Controller: ApplicationReadModel received
    deactivate SubGateway
    Controller-->>Client: 201 Created + Location: /api/v0/applications/{id}
```

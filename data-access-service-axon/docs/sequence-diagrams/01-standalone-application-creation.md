# Standalone Application Creation

Application creation always creates an unlinked application. Linking is performed later through the
explicit application-link endpoint.

```mermaid
sequenceDiagram
    actor Client
    participant Controller as ApplicationCommandController
    participant CmdGateway as CommandGateway
    participant AppAggregate as ApplicationAggregate
    participant Factory as ApplicationCreationDetailsFactory
    participant RouteProjection as ApplicationGroupRouteProjection<br/>(subscribing)
    participant Projection as ApplicationProjection<br/>(tracking)

    Client->>Controller: POST /api/v0/applications

    Controller->>CmdGateway: sendAndWait(CreateApplicationCommand)
    CmdGateway->>AppAggregate: handle(CreateApplicationCommand) [CREATE_IF_MISSING]
    AppAggregate->>Factory: prepare(command)
    Factory-->>AppAggregate: ApplicationCreationDetails (creation-only fields)
    AppAggregate->>AppAggregate: apply(ApplicationCreatedEvent)
    Note over AppAggregate: No linked-group commands are dispatched

    AppAggregate-->>CmdGateway: applicationId
    CmdGateway-->>Controller: applicationId

    RouteProjection->>RouteProjection: on(ApplicationCreatedEvent) [subscribing]
    RouteProjection->>RouteProjection: save STANDALONE route
    Projection->>Projection: on(ApplicationCreatedEvent) [async]
    Projection->>Projection: save unlinked ApplicationReadModel
    Note over Controller,Projection: Controller does not wait for tracking projections
    Controller-->>Client: 201 Created + Location: /api/v0/applications/{id}
```

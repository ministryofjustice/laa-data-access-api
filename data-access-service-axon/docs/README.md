# Axon Module Developer Guide

This directory explains the design of `data-access-service-axon`. Start here when changing command
handling, application linking, projections, or the storage of sensitive application data.

The module currently uses **Axon Framework 4.11.2**. Developers with only introductory Axon
experience should follow the learning path below before using the documents as a reference.

## Suggested learning path

1. [Axon in this module](axon-in-this-module.md) — build the execution model.
2. [Worked decision example](worked-example-decision.md) — follow one request through real classes.
3. [Testing Axon code](testing-axon-code.md) — choose the right test boundary.
4. [Guardrails and common mistakes](guardrails.md) — learn the module's safety rules.
5. [Event evolution](event-evolution.md) — understand the long-lived event contract.
6. [Onboarding exercise](onboarding-exercise.md) — inspect the complete flow yourself.

## Guides

| Guide | Read this when you need to understand |
|---|---|
| [Axon in this module](axon-in-this-module.md) | How commands, aggregates, events, processors, and projections execute |
| [Worked decision example](worked-example-decision.md) | How one real endpoint travels through the implementation |
| [Testing Axon code](testing-axon-code.md) | Which test style to use for aggregates, handlers, processors, and PostgreSQL |
| [Guardrails](guardrails.md) | Common mistakes and rules that keep replay and boundaries safe |
| [Event evolution](event-evolution.md) | How to change persisted event contracts without breaking replay |
| [Onboarding exercise](onboarding-exercise.md) | A practical application, event, data-version, and projection walkthrough |
| [Architecture overview](architecture.md) | The main components, command/query paths, consistency boundaries, and version numbers |
| [Linked applications](linked-applications.md) | How applications form groups, why the event router is synchronous, and where linking rules live |
| [Events and sensitive data](events-and-sensitive-data.md) | Why events are thin, how `application_data` versions are connected to events, and what retention means |
| [Projections and replay](projections-and-replay.md) | Which read models exist, how they are hydrated, and how reset/replay behaves |
| [Storage model](storage-model.md) | Which tables are authoritative or disposable and how their identifiers and versions relate |
| [Failure behaviour](failure-behaviour.md) | Expected API, transaction, projection, and recovery outcomes for common failures |
| [Running and operating](running-and-operating.md) | Local startup, tests, store inspection, processor recovery, and retention operations |
| [Glossary](glossary.md) | Axon and module-specific terminology used throughout these guides |
| [Sequence diagrams](sequence-diagrams/README.md) | Step-by-step application creation and linking flows |
| [Architecture decisions](adr/README.md) | Why significant design choices were made and when they should be revisited |

## Useful code entry points

- `ApplicationCommandController` accepts write requests and dispatches commands.
- `ApplicationAggregate` owns the state and rules of one application.
- `LinkedApplicationGroupAggregate` owns the lead and membership of a linked group.
- `ApplicationGroupEventRouter` coordinates group creation synchronously.
- `ApplicationDataStore` reads and appends immutable sensitive-data versions.
- `ApplicationProjection` and `ApplicationHistoryProjection` build query-side views.

The generated OpenAPI types are the public API contract. Commands and events are internal messages
and should not be exposed directly by controllers.

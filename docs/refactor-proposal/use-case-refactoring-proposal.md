# Use Case Architecture Refactoring Proposal

## Executive Summary

This document proposes a refactoring of the LAA Data Access API from a traditional service-layer architecture to a use-case-driven architecture aligned with Clean Architecture and Hexagonal Architecture principles. The refactoring will improve maintainability, testability, and separation of concerns while maintaining the existing API contracts.

## Current Architecture Analysis

### Existing Structure

The current architecture follows a traditional layered approach:

```
controller/ → service/ → repository/ → entity/
```

#### Controller Layer
- **ApplicationController**: Handles 8 distinct operations across application lifecycle
  - `createApplication()` - Create new application
  - `updateApplication()` - Update application details
  - `getApplications()` - Search/list applications with filtering and pagination
  - `getApplicationById()` - Retrieve single application
  - `assignCaseworker()` - Assign caseworker(s) to application(s)
  - `unassignCaseworker()` - Remove caseworker assignment
  - `getApplicationHistory()` - Retrieve application event history
  - `makeDecision()` - Make decision on application

- **CaseworkerController**: Handles caseworker operations
  - `getCaseworkers()` - List all caseworkers

- **IndividualsController**: Handles individual entity operations
  - `getIndividuals()` - Search/list individuals with filtering and pagination

#### Service Layer
Current services identified:
- `ApplicationService`
- `ApplicationSummaryService`
- `ApplicationContentParserService`
- `DomainEventService`
- `CaseworkerService`
- `IndividualsService`
- `ProceedingsService`

#### Repository Layer
Spring Data JPA repositories:
- `ApplicationRepository`
- `ApplicationSummaryRepository`
- `CaseworkerRepository`
- `IndividualRepository`
- `DomainEventRepository`
- `ProceedingRepository`
- `DecisionRepository`
- `MeritsDecisionRepository`
- `CertificateRepository`

### Current Architecture Limitations

1. **Coupling**: Controllers are tightly coupled to Spring Data JPA repositories through services
2. **Business Logic Dispersion**: Business logic scattered across multiple service classes
3. **Testing Complexity**: Difficult to test business logic without mocking framework-specific components
4. **Limited Flexibility**: Hard to swap persistence mechanisms or adapt to new storage requirements
5. **Unclear Boundaries**: No explicit separation between domain logic and infrastructure concerns

## Proposed Use Case Architecture

### Architectural Principles

The refactored architecture will follow:

1. **Dependency Inversion Principle**: High-level modules (use cases) depend on abstractions (ports), not concrete implementations
2. **Single Responsibility Principle**: Each use case handles one business operation
3. **Interface Segregation**: Small, focused port interfaces instead of large repository interfaces
4. **Framework Independence**: Domain and application layers have no Spring or JPA dependencies

### Layered Structure

```
presentation/ (controller)
    ↓ (depends on)
application/ (use cases)
    ↓ (depends on)
domain/ (model + ports)
    ↑ (implemented by)
infrastructure/ (adapters: persistence, messaging, external APIs)
```

## Identified Use Cases

### Application Domain Use Cases

#### Create & Update Operations
- **CreateApplicationUseCase**
  - Input: `CreateApplicationRequest`
  - Output: `ApplicationCreatedResponse` (containing UUID)
  - Dependencies: `ApplicationPort`, `ValidationPort`
  - Business Logic: Validate application data, generate UUID, persist application

- **UpdateApplicationUseCase**
  - Input: `UpdateApplicationRequest` (including applicationId)
  - Output: `ApplicationUpdatedResponse`
  - Dependencies: `ApplicationPort`, `ValidationPort`, `DomainEventPort`
  - Business Logic: Validate updates, retrieve existing application, apply changes, persist, emit event

#### Query Operations
- **GetApplicationByIdUseCase**
  - Input: `GetApplicationRequest` (applicationId)
  - Output: `ApplicationResponse`
  - Dependencies: `ApplicationPort`
  - Business Logic: Retrieve and return application, handle not found

- **SearchApplicationsUseCase**
  - Input: `SearchApplicationsRequest` (filters: status, laaReference, clientName, DOB, userId, isAutoGranted, matterType, sorting, pagination)
  - Output: `PaginatedApplicationsResponse`
  - Dependencies: `ApplicationSummaryPort`
  - Business Logic: Apply filters, sort, paginate, return results

#### Caseworker Assignment Operations
- **AssignCaseworkerUseCase**
  - Input: `AssignCaseworkerRequest` (caseworkerId, applicationIds[], eventHistory)
  - Output: `CaseworkerAssignedResponse`
  - Dependencies: `ApplicationPort`, `CaseworkerPort`, `DomainEventPort`
  - Business Logic: Validate caseworker exists, validate applications exist, assign caseworker, record event history

- **UnassignCaseworkerUseCase**
  - Input: `UnassignCaseworkerRequest` (applicationId, eventHistory)
  - Output: `CaseworkerUnassignedResponse`
  - Dependencies: `ApplicationPort`, `DomainEventPort`
  - Business Logic: Verify application exists, remove assignment, record event

#### History & Decision Operations
- **GetApplicationHistoryUseCase**
  - Input: `GetApplicationHistoryRequest` (applicationId, eventTypes[])
  - Output: `ApplicationHistoryResponse`
  - Dependencies: `DomainEventPort`
  - Business Logic: Retrieve filtered event history for application

- **MakeDecisionUseCase**
  - Input: `MakeDecisionRequest` (applicationId, decision details)
  - Output: `DecisionMadeResponse`
  - Dependencies: `ApplicationPort`, `DecisionPort`, `DomainEventPort`
  - Business Logic: Validate application state, record decision, update application status, emit events

### Caseworker Domain Use Cases

- **GetAllCaseworkersUseCase**
  - Input: `GetCaseworkersRequest` (serviceName)
  - Output: `CaseworkersResponse`
  - Dependencies: `CaseworkerPort`
  - Business Logic: Retrieve all active caseworkers

### Individual Domain Use Cases

- **SearchIndividualsUseCase**
  - Input: `SearchIndividualsRequest` (pagination, applicationId filter, type filter)
  - Output: `PaginatedIndividualsResponse`
  - Dependencies: `IndividualPort`
  - Business Logic: Apply filters, paginate, return individuals

## Port Definitions

### Application Domain Ports

#### ApplicationPort
Purpose: Primary interface for application persistence operations
Methods:
- `save(Application) → Application`
- `findById(UUID) → Optional<Application>`
- `findByApplyApplicationId(UUID) → Optional<Application>`
- `existsByApplyApplicationId(UUID) → boolean`
- `findAllByApplyApplicationIds(List<UUID>) → List<Application>`
- `delete(UUID) → void`

#### ApplicationSummaryPort
Purpose: Optimized interface for application search/listing operations
Methods:
- `findByCriteria(SearchCriteria, Pageable) → Page<ApplicationSummary>`
- `countByCriteria(SearchCriteria) → long`

#### CaseworkerPort
Purpose: Interface for caseworker operations
Methods:
- `findById(UUID) → Optional<Caseworker>`
- `findAll() → List<Caseworker>`
- `existsById(UUID) → boolean`

#### IndividualPort
Purpose: Interface for individual operations
Methods:
- `findAll(Pageable) → Page<Individual>`
- `findByApplicationId(UUID, Pageable) → Page<Individual>`
- `findByType(IndividualType, Pageable) → Page<Individual>`
- `findByApplicationIdAndType(UUID, IndividualType, Pageable) → Page<Individual>`

#### DomainEventPort
Purpose: Interface for domain event persistence and retrieval
Methods:
- `save(DomainEvent) → DomainEvent`
- `findByApplicationId(UUID, List<DomainEventType>) → List<DomainEvent>`
- `publish(DomainEvent) → void`

#### DecisionPort
Purpose: Interface for decision persistence
Methods:
- `save(Decision) → Decision`
- `findByApplicationId(UUID) → Optional<Decision>`

### Supporting Ports

#### ValidationPort
Purpose: Interface for business rule validation
Methods:
- `validateApplication(Application) → ValidationResult`
- `validateDecision(Decision, Application) → ValidationResult`

#### ProceedingPort
Purpose: Interface for proceeding operations
Methods:
- `findByApplicationId(UUID) → List<Proceeding>`
- `save(Proceeding) → Proceeding`

## Repository Adapter Implementation

### Implementation Strategy

Each port will be implemented by an adapter that wraps the existing Spring Data JPA repositories.

#### Adapter Package Structure
```
infrastructure/
  persistence/
    adapter/
      ApplicationRepositoryAdapter.java
      ApplicationSummaryRepositoryAdapter.java
      CaseworkerRepositoryAdapter.java
      IndividualRepositoryAdapter.java
      DomainEventRepositoryAdapter.java
      DecisionRepositoryAdapter.java
    jpa/
      ApplicationJpaRepository.java (existing ApplicationRepository renamed)
      CaseworkerJpaRepository.java (existing CaseworkerRepository renamed)
      IndividualJpaRepository.java (existing IndividualRepository renamed)
      DomainEventJpaRepository.java (existing DomainEventRepository renamed)
      etc.
    mapper/
      ApplicationEntityMapper.java
      CaseworkerEntityMapper.java
      IndividualEntityMapper.java
      etc.
```

### Adapter Responsibilities

Adapters will:
1. Implement port interfaces
2. Delegate to Spring Data JPA repositories
3. Map between domain models and JPA entities
4. Handle persistence-specific concerns (transactions at adapter level if needed)
5. Translate persistence exceptions to domain exceptions

### Mapper Pattern

Mappers will convert between:
- Domain models (in `domain/model/`) - pure Java objects, no JPA annotations
- JPA entities (in `infrastructure/persistence/entity/`) - existing entity classes with JPA annotations

This separation allows domain models to remain framework-independent.

## Package Structure

### Proposed Organization

```
uk.gov.justice.laa.dstew.access/
  ├── domain/
  │   ├── model/
  │   │   ├── Application.java (domain model)
  │   │   ├── Caseworker.java
  │   │   ├── Individual.java
  │   │   ├── DomainEvent.java
  │   │   ├── Decision.java
  │   │   ├── Proceeding.java
  │   │   └── valueobjects/
  │   │       ├── ApplicationStatus.java
  │   │       ├── MatterType.java
  │   │       └── ServiceName.java
  │   └── ports/
  │       ├── outbound/ (driven ports - implemented in infrastructure)
  │       │   ├── ApplicationPort.java
  │       │   ├── ApplicationSummaryPort.java
  │       │   ├── CaseworkerPort.java
  │       │   ├── IndividualPort.java
  │       │   ├── DomainEventPort.java
  │       │   ├── DecisionPort.java
  │       │   └── ValidationPort.java
  │       └── inbound/ (driving ports - use case interfaces)
  │           ├── application/
  │           │   ├── CreateApplicationUseCase.java
  │           │   ├── UpdateApplicationUseCase.java
  │           │   ├── GetApplicationByIdUseCase.java
  │           │   ├── SearchApplicationsUseCase.java
  │           │   ├── AssignCaseworkerUseCase.java
  │           │   ├── UnassignCaseworkerUseCase.java
  │           │   ├── GetApplicationHistoryUseCase.java
  │           │   └── MakeDecisionUseCase.java
  │           ├── caseworker/
  │           │   └── GetAllCaseworkersUseCase.java
  │           └── individual/
  │               └── SearchIndividualsUseCase.java
  ├── application/
  │   ├── usecases/
  │   │   ├── application/
  │   │   │   ├── CreateApplicationUseCaseImpl.java
  │   │   │   ├── UpdateApplicationUseCaseImpl.java
  │   │   │   ├── GetApplicationByIdUseCaseImpl.java
  │   │   │   ├── SearchApplicationsUseCaseImpl.java
  │   │   │   ├── AssignCaseworkerUseCaseImpl.java
  │   │   │   ├── UnassignCaseworkerUseCaseImpl.java
  │   │   │   ├── GetApplicationHistoryUseCaseImpl.java
  │   │   │   └── MakeDecisionUseCaseImpl.java
  │   │   ├── caseworker/
  │   │   │   └── GetAllCaseworkersUseCaseImpl.java
  │   │   └── individual/
  │   │       └── SearchIndividualsUseCaseImpl.java
  │   └── dto/
  │       ├── request/
  │       │   ├── CreateApplicationRequest.java
  │       │   ├── UpdateApplicationRequest.java
  │       │   ├── SearchApplicationsRequest.java
  │       │   ├── AssignCaseworkerRequest.java
  │       │   ├── UnassignCaseworkerRequest.java
  │       │   ├── MakeDecisionRequest.java
  │       │   └── SearchIndividualsRequest.java
  │       └── response/
  │           ├── ApplicationResponse.java
  │           ├── ApplicationCreatedResponse.java
  │           ├── PaginatedApplicationsResponse.java
  │           ├── ApplicationHistoryResponse.java
  │           ├── CaseworkersResponse.java
  │           └── PaginatedIndividualsResponse.java
  ├── infrastructure/
  │   ├── persistence/
  │   │   ├── adapter/
  │   │   │   ├── ApplicationRepositoryAdapter.java
  │   │   │   ├── ApplicationSummaryRepositoryAdapter.java
  │   │   │   ├── CaseworkerRepositoryAdapter.java
  │   │   │   ├── IndividualRepositoryAdapter.java
  │   │   │   ├── DomainEventRepositoryAdapter.java
  │   │   │   └── DecisionRepositoryAdapter.java
  │   │   ├── jpa/
  │   │   │   ├── ApplicationJpaRepository.java
  │   │   │   ├── ApplicationSummaryJpaRepository.java
  │   │   │   ├── CaseworkerJpaRepository.java
  │   │   │   ├── IndividualJpaRepository.java
  │   │   │   ├── DomainEventJpaRepository.java
  │   │   │   └── DecisionJpaRepository.java
  │   │   ├── entity/ (existing entity package)
  │   │   │   ├── ApplicationEntity.java
  │   │   │   ├── CaseworkerEntity.java
  │   │   │   ├── IndividualEntity.java
  │   │   │   └── etc.
  │   │   └── mapper/
  │   │       ├── ApplicationEntityMapper.java
  │   │       ├── CaseworkerEntityMapper.java
  │   │       └── etc.
  │   ├── messaging/ (future - event publishing adapters)
  │   └── external/ (future - external API adapters)
  ├── presentation/
  │   └── rest/
  │       ├── ApplicationController.java (refactored)
  │       ├── CaseworkerController.java (refactored)
  │       └── IndividualsController.java (refactored)
  ├── config/ (existing config package)
  ├── exception/ (existing exception package)
  ├── security/ (existing security package)
  ├── utils/ (existing utils package)
  └── validation/ (existing validation package)
```

## Migration Strategy

### Phase 1: Preparation (Week 1)
1. Create new package structure without breaking existing code
2. Define all port interfaces in `domain/ports/outbound/`
3. Define use case interfaces in `domain/ports/inbound/`
4. Create DTO classes for requests and responses

### Phase 2: Domain Layer (Week 2)
1. Create or refactor domain models in `domain/model/`
2. Ensure domain models are framework-independent
3. Move value objects to appropriate package
4. Write unit tests for domain models

### Phase 3: Infrastructure Adapters (Week 2-3)
1. Create mapper classes for entity-to-domain conversion
2. Implement repository adapters that implement port interfaces
3. Wire adapters to existing Spring Data repositories
4. Write integration tests for adapters

### Phase 4: Use Case Implementation (Week 3-4)
1. Implement use cases one at a time, starting with simplest (GetCaseworkers)
2. Use cases depend on port interfaces, not concrete implementations
3. Write unit tests for each use case using port mocks
4. Validate business logic correctness

### Phase 5: Controller Refactoring (Week 4-5)
1. Refactor controllers to use use case interfaces
2. Remove direct service dependencies
3. Ensure API contracts remain unchanged
4. Update integration tests

### Phase 6: Service Layer Deprecation (Week 5-6)
1. Gradually remove old service classes as use cases replace them
2. Ensure no code references old services
3. Clean up unused dependencies

### Phase 7: Testing & Validation (Week 6)
1. Run full test suite (unit, integration, end-to-end)
2. Verify no breaking changes to API
3. Performance testing to ensure no regressions
4. Code review and refactoring refinements

### Phase 8: Documentation & Training (Week 7)
1. Update technical documentation
2. Create architecture decision records (ADRs)
3. Team training on new architecture
4. Update developer onboarding materials

## Benefits

### Maintainability
- **Clear boundaries**: Each use case encapsulates one business operation
- **Reduced coupling**: Controllers and use cases depend on abstractions
- **Easier navigation**: Package structure reflects business domains

### Testability
- **Isolated testing**: Use cases can be unit tested without Spring context
- **Mock simplicity**: Port interfaces are easier to mock than concrete services
- **Test clarity**: One use case = one test suite

### Flexibility
- **Persistence independence**: Can swap JPA for other persistence mechanisms
- **Multiple adapters**: Can support multiple data sources simultaneously
- **API evolution**: Can add GraphQL or gRPC controllers using same use cases

### Domain Focus
- **Business logic visibility**: Use cases clearly express business operations
- **Framework independence**: Domain models free from infrastructure concerns
- **Ubiquitous language**: Use case names reflect business terminology

### Team Collaboration
- **Parallel development**: Teams can work on different use cases independently
- **Clear ownership**: Each use case has clear responsibilities
- **Onboarding**: New developers can understand business logic through use cases

## Risks & Mitigations

### Risk: Increased Code Volume
**Mitigation**: Use code generation tools for mappers (MapStruct), keep interfaces focused

### Risk: Over-Engineering
**Mitigation**: Start with critical use cases, refactor incrementally, measure value

### Risk: Learning Curve
**Mitigation**: Pair programming, code reviews, documentation, training sessions

### Risk: Migration Complexity
**Mitigation**: Phased approach, maintain backward compatibility, comprehensive testing

### Risk: Performance Overhead
**Mitigation**: Benchmark critical paths, optimize mappers, use caching where appropriate

## Success Metrics

1. **Code Coverage**: Maintain or increase unit test coverage above 80%
2. **Build Time**: No significant increase in build/test execution time
3. **Defect Rate**: Monitor production defects for 3 months post-migration
4. **Developer Velocity**: Measure feature delivery time after team adaptation (3-6 months)
5. **Code Quality**: SonarQube maintainability rating improvement

## Recommendations

1. **Start Small**: Begin with GetAllCaseworkersUseCase (simplest use case)
2. **Iterative Approach**: Refactor one domain at a time (Caseworker → Individual → Application)
3. **Team Buy-in**: Conduct architecture review sessions with development team
4. **Continuous Integration**: Ensure all tests pass at each phase
5. **Documentation First**: Create ADRs before major architectural changes
6. **Monitoring**: Add metrics to track performance and error rates during migration

## Related Resources

- **Clean Architecture** by Robert C. Martin
- **Hexagonal Architecture** by Alistair Cockburn
- **Domain-Driven Design** by Eric Evans
- **Patterns of Enterprise Application Architecture** by Martin Fowler
- **Spring Documentation**: Dependency Injection and Inversion of Control

## Appendix

### A. Glossary

- **Port**: An interface defining a contract between layers
- **Adapter**: A concrete implementation of a port interface
- **Use Case**: A single business operation encapsulating specific business logic
- **Domain Model**: Framework-independent representation of business entities
- **Inbound Port**: Interface driven by external actors (controllers)
- **Outbound Port**: Interface for calling external systems (persistence, messaging)

### B. Existing Services Analysis

Current services that will be decomposed into use cases:

- **ApplicationService**: Contains 6+ different business operations → 8 use cases
- **ApplicationSummaryService**: Search/listing operations → 1 use case
- **DomainEventService**: Event retrieval → 1 use case (part of history use case)
- **CaseworkerService**: Simple retrieval → 1 use case
- **IndividualsService**: Search/listing operations → 1 use case

### C. Transaction Management

Transactions will be managed at the use case level using Spring's `@Transactional` annotation on use case implementations. This ensures that each business operation is atomic and consistent.

### D. Exception Handling

Domain exceptions will be defined in `domain/exception/` and will be:
- Thrown by use cases for business rule violations
- Caught by controllers and translated to appropriate HTTP responses
- Independent of infrastructure concerns

### E. Logging & Monitoring

Existing logging aspects (`@LogMethodArguments`, `@LogMethodResponse`) can be applied to:
- Use case implementations for business operation logging
- Controller methods for API request/response logging
- Adapters for persistence operation logging

---

**Document Version**: 1.0  
**Date**: March 13, 2026  
**Author**: Technical Architecture Team  
**Status**: Proposal - Awaiting Approval


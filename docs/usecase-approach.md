
# Use Case Architecture Refactoring Proposal (Revised with Proper References)

## Executive Summary
This proposal outlines a refactoring of the LAA Data Access API from a traditional layered architecture into a use‑case‑driven model aligned with Clean Architecture and Hexagonal Architecture principles. These approaches emphasise domain‑centric design, inward-facing dependencies, and strong separation of concerns.

**References:**
- [Hexagonal Architecture & Clean Architecture overview – DEV Community][1]
- [Software Architecture Patterns: Layered, Hexagonal, Clean – CalmOps][2]

---

## Current Architecture Analysis
### Existing Structure
The current architecture follows:
```
controller/ → service/ → repository/ → entity/
```
This layered approach often leads to tight coupling, difficulty changing infrastructure, and scattering of business logic.

**Reference:**
- [Hexagonal vs Layered Architecture comparison – SystemsArchitect][3]

### Problems Identified
1. Tight coupling between layers
2. Distributed business logic
3. Test complexity due to framework dependencies
4. Limited flexibility
5. Weak separation of domain and infrastructure

**Reference:**
- [SystemsArchitect layered architecture limitations][3]

---

## Proposed Use Case Architecture
The refactored system applies Clean Architecture and Hexagonal Architecture principles. Clean Architecture promotes dependency inversion and pure domain models, while Hexagonal Architecture formalises ports and adapters.

**References:**
- [Clean Architecture fundamentals – DEV Community][1]
- [Ports & Adapters concept – CalmOps][2]

### Architectural Principles
- **Dependency Inversion:** Use cases depend on abstractions, not infrastructure.
- **Single Responsibility:** Each use case represents one business operation.
- **Interface Segregation:** Ports remain small and focused.
- **Framework Independence:** Domain code contains no annotations.

**Reference:**
- [SOLID in Clean Architecture – Python patterns article][4]

---

## Target Layered Structure
```
presentation/
  ↓
application/ (use cases)
  ↓
domain/ (models + ports)
  ↑
infrastructure/ (adapters)
```
**Reference:** [Clean Architecture layering guidance][1]

---

## Identified Use Cases
### Application Domain
- CreateApplicationUseCase
- UpdateApplicationUseCase
- GetApplicationByIdUseCase
- SearchApplicationsUseCase
- AssignCaseworkerUseCase
- UnassignCaseworkerUseCase
- GetApplicationHistoryUseCase
- MakeDecisionUseCase

### Caseworker Domain
- GetAllCaseworkersUseCase

### Individual Domain
- SearchIndividualsUseCase

**Reference:** [Clean Architecture use-case orientation][1]

---

## Port Definitions
### Outbound Ports
- ApplicationPort
- ApplicationSummaryPort
- CaseworkerPort
- IndividualPort
- DomainEventPort
- DecisionPort

**Reference:** [Ports as abstractions – CalmOps][2]

### Inbound Ports
Use cases expose inbound interfaces representing business operations.

**Reference:** [Backend architecture patterns][5]

---

## Repository Adapter Strategy
Adapters implement outbound ports and:
- Convert domain models to JPA entities
- Encapsulate database operations
- Translate persistence exceptions into domain exceptions

**Reference:** [Ports & Adapters adapter model][2]

---

## Proposed Package Structure
```
domain/
  model/
  ports/
    inbound/
    outbound/
application/
  usecases/
  dto/
infrastructure/
  persistence/
    adapter/
    jpa/
    mapper/
presentation/
  rest/
```
**Reference:** [Architecture pattern file structure – GitHub][5]

---

## Migration Strategy
1. Introduce new package structure
2. Create pure domain models and ports
3. Implement adapters
4. Implement use cases incrementally
5. Refactor controllers
6. Deprecate service layer
7. Perform full test coverage
8. Update documentation and train team

**Reference:** [Migration guidelines – SystemsArchitect][3]

---

## Benefits
- Strong isolation of business logic
- Highly testable use cases via mocks
- Replaceable infrastructure components
- Clear domain boundaries
- Supports multiple interfaces (REST, GraphQL, gRPC)

**References:**
- [Clean Architecture benefits][1]
- [Hexagonal Architecture adaptability][2]

---

## Risks & Mitigations
| Risk | Mitigation |
|------|------------|
| Increased code volume | Use code generation tools |
| Learning curve | Provide training and ADRs |
| Migration complexity | Use phased approach |
| Potential over‑engineering | Apply selectively to complex areas |

**Reference:** [Decision guidance on Clean vs Layered][3]

---

## References

[1]: https://dev.to/dyarleniber/hexagonal-architecture-and-clean-architecture-with-examples-48oi "Hexagonal Architecture & Clean Architecture – DEV Community"
[2]: https://calmops.com/software-engineering/software-architecture-patterns-layered-hexagonal-clean/ "Software Architecture Patterns – CalmOps"
[3]: https://www.systemsarchitect.io/blog/hexagonal-clean-architecture-vs-layered-n-tier-architecture-dc025 "Hexagonal vs Layered Architecture – SystemsArchitect"
[4]: https://www.glukhov.org/post/2025/11/python-design-patterns-for-clean-architecture/ "Python Design Patterns for Clean Architecture"
[5]: https://github.com/openclaw/skills/tree/main/skills/wpank/architecture-patterns "Backend Architecture Patterns – GitHub"

### Additional Reading
- [Clean Architecture + DDD in Practice 2025](https://wojciechowski.app/en/articles/clean-architecture-domain-driven-design-2025)


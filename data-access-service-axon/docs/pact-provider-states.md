# Pact provider states

This is the catalogue of provider states `laa-data-access-api` implements for Pact consumer-driven
contract tests. Consumers (`laa-civil-decide-api`, `laa-apply-for-legal-aid`) copy a state string
from here into `.given("...")`. The string must match exactly, so do not retype it.

The implementation lives in `src/pactTest`. `PactStates` holds the strings, `ProviderStates` builds
each state through the real use cases, and `DataAccessApiProviderTests` replays the contracts.

## How verification works

- The real application runs on an in-memory database inside the test. Nothing below the controllers
  is mocked, except the Secure Document Storage client, which is replaced the same way the
  integration tests replace it until a contract with the SDS team exists.
- Before each interaction, Pact calls the handler for its state. The handler creates the data by
  dispatching real commands and waits until the projection can serve it. Then Pact replays the
  consumer's request against the real endpoint and compares the response.
- Each interaction is verified on its own. Nothing carries over from one interaction to the next, so
  a pact must never rely on a POST having run before a GET.

## Rules consumers need to know

1. **Send your real headers.** Only the `Authorization` bearer token is replaced at replay time.
   `X-Service-Name` and everything else is sent exactly as written in the pact.
2. **The caller is always the dev-token caseworker**, Entra OID
   `00000000-0000-0000-0000-000000000001`. "Assigned to the caseworker" means assigned to that
   identity, so decisions and unassignments replay as that user.
3. **Use the identifiers a state guarantees.** They are fixed. A GET for any other ID returns 404.
4. **Assert by type, not value**, except for the identifiers you chose. Names, references and
   timestamps are stable but incidental.
5. **There are no "nothing exists" states.** The database is shared across the run, so emptiness
   cannot be guaranteed. Express an empty result with a filter nothing matches, or use a reserved
   missing ID for 404 cases.
6. **Document identifiers are injected.** The service generates them, so the document state returns
   `documentId` and your pact uses Pact's provider-state injected value for it (see below).
7. **Agree a new state before publishing a pact that needs it.** A state with no handler fails
   verification with "missing state". Raise it with the Data Access team and the handler lands with
   your pact.

## Applications

| State string | Guarantees | Not guaranteed | Typical endpoints |
| --- | --- | --- | --- |
| `applications exist` | Application `…0001`, submitted, awaiting manual assessment, in the list. Reference `LAA-REF-0001`, client Alice Anderson | `isLead` is false. Position in the list | GET applications |
| `a client individual exists for application 00000000-0000-0000-0000-000000000001` | GET individuals for `…0001` returns one CLIENT, Alice Anderson, DOB 1980-01-01 | No `clientId` is returned | GET individuals |
| `that no matching special children act application already exists` | The ID you send in the body does not exist. Create succeeds, Location echoes your ID | Whether 201 or 202 is returned depends on projection timing; both are valid | POST applications |
| `a submitted application 00000000-0000-0000-0000-000000000002 exists` | Submitted, auto-grant pending, application version 0, no decision, no notes, not linked | Anything time-based | GET by id, PATCH, GET history, PATCH auto-grant-outcome |
| `application 00000000-0000-0000-0000-000000000003 is ready for manual assessment and assigned to the caseworker` | Manual assessment recorded, assigned to the dev caseworker, application version 1, assignment version 1 | | PATCH decision, POST work-list unassign |
| `application 00000000-0000-0000-0000-000000000004 is ready for manual assessment and unassigned` | Manual assessment recorded, unassigned, assignment version 0, on the work list | | POST work-list assign, GET applications filtered by MANUAL |
| `application 00000000-0000-0000-0000-000000000005 has been granted` | Granted after manual assessment by the dev caseworker, certificate present, history holds create, ready, assign and decision events | Decision reasons text | GET certificate, GET history, PATCH decision expecting 409 |
| `application 00000000-0000-0000-0000-000000000006 has notes` | Submitted with at least one note | Note count beyond one | GET notes, POST notes |
| `applications 00000000-0000-0000-0000-000000000007 and 00000000-0000-0000-0000-000000000008 are linked with 007 as lead` | Both submitted, one family group, `…0007` is lead, `…0008` is member, linked group version 0 | | GET by id with linked group, POST unlink, POST make-lead |
| `no application exists with id 00000000-0000-0000-0000-00000000dead` | That ID is never created | | Any application endpoint expecting 404 |
| `the application read model is lagging` | The application projection is paused for this interaction, so POST applications returns 202 with a Location after the configured wait | | POST applications expecting 202 |

## Work list

| State string | Guarantees | Typical endpoints |
| --- | --- | --- |
| `work list items exist` | At least three items: application `…0003` assigned to the dev caseworker, application `…0004` unassigned, prior authority `…a002` submitted and unassigned | GET work-list, with and without filters |

## Prior authorities

All prior authorities below belong to parent application `…0009`, which is auto-granted. They are
EXPERT type with a complete, submittable content.

| State string | Guarantees | Typical endpoints |
| --- | --- | --- |
| `a prior authority draft 00000000-0000-0000-0000-00000000a001 exists` | Status DRAFT, no documents | GET, PUT draft, POST submit, POST documents |
| `a submitted prior authority 00000000-0000-0000-0000-00000000a002 exists` | Status SUBMITTED, unassigned, version 0 | GET, POST work-list assign |
| `a submitted prior authority 00000000-0000-0000-0000-00000000a003 is assigned to the caseworker` | Status SUBMITTED, assigned to the dev caseworker, version 0 | PATCH decision, POST work-list unassign |
| `a decided prior authority 00000000-0000-0000-0000-00000000a004 exists` | Status DECIDED, granted | GET, PATCH decision expecting 409 |
| `a prior authority draft 00000000-0000-0000-0000-00000000a005 has an uploaded document` | Status DRAFT with one PDF named `evidence.pdf`. Returns `documentId` as an injected value | GET, PATCH, DELETE a document |
| `no prior authority exists with id 00000000-0000-0000-0000-0000000adead` | That ID is never created | Any prior authority endpoint expecting 404 |

### Using the injected document id

The handler for the document state returns `{"documentId": "<uuid>"}`. In a Pact JVM consumer test
the request path takes it from the provider state:

```java
builder
    .given("a prior authority draft 00000000-0000-0000-0000-00000000a005 has an uploaded document")
    .uponReceiving("download a prior authority document")
    .pathFromProviderState(
        "/api/v0/prior-authorities/00000000-0000-0000-0000-00000000a005/documents/${documentId}",
        "/api/v0/prior-authorities/00000000-0000-0000-0000-00000000a005/documents/"
            + "11111111-1111-1111-1111-111111111111")
    .method("GET")
```

The second argument is the example used by the consumer's own mock; verification replaces it with
the real identifier.

## Running verification

- Every pull request runs `pactBootCheck`, which boots the provider context and seeds every state
  without the broker. A state that can no longer be built fails the build here.
- Every main build runs `pactTest` against the broker and publishes results for that commit.
- A consumer publishing a pact triggers the `Pact triggered workflow`, which verifies that consumer
  and branch.
- Locally, with broker credentials in the environment:

```bash
PACT_PUBLISH_RESULTS=false ./gradlew :data-access-service-axon:pactTest
```

Pending pacts are enabled: a consumer's new or changed pact does not fail the provider build until it
has been verified successfully once.

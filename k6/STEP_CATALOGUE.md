# k6 step catalogue

This file is generated from `k6/lib/step-registry.js`. Regenerate it with
`node k6/scripts/generate-step-catalogue.js`; validate it with
`node k6/scripts/validate-documentation.js`.

## Start application draft

| Metadata | Value |
| --- | --- |
| Actor | Provider |
| Inputs | `runContext`, `fixtures/application/create.json` |
| Outputs | `applicationDraftId` |
| HTTP interaction | `POST /api/v0/application-drafts` |
| Expected observable result | Returns HTTP 201 and an Application draft ID. |
| Tags | `smoke`, `performance`, `application`, `draft` |

## Update application draft: 01

| Metadata | Value |
| --- | --- |
| Actor | Provider |
| Inputs | `applicationDraft`, `fixtures/application/update-01.json` |
| Outputs | `applicationDraft` |
| HTTP interaction | `Local fixture composition for POST /api/v0/application-drafts` |
| Expected observable result | The named fixture is recorded in the composed draft request. |
| Tags | `smoke`, `performance`, `application`, `draft` |

## Update application draft: 02

| Metadata | Value |
| --- | --- |
| Actor | Provider |
| Inputs | `applicationDraft`, `fixtures/application/update-02.json` |
| Outputs | `applicationDraft` |
| HTTP interaction | `Local fixture composition for POST /api/v0/application-drafts` |
| Expected observable result | The named fixture is recorded in the composed draft request. |
| Tags | `smoke`, `performance`, `application`, `draft` |

## Update application draft: 03

| Metadata | Value |
| --- | --- |
| Actor | Provider |
| Inputs | `applicationDraft`, `fixtures/application/update-03.json` |
| Outputs | `applicationDraft` |
| HTTP interaction | `Local fixture composition for POST /api/v0/application-drafts` |
| Expected observable result | The named fixture is recorded in the composed draft request. |
| Tags | `smoke`, `performance`, `application`, `draft` |

## Update application draft: 04

| Metadata | Value |
| --- | --- |
| Actor | Provider |
| Inputs | `applicationDraft`, `fixtures/application/update-04.json` |
| Outputs | `applicationDraft` |
| HTTP interaction | `Local fixture composition for POST /api/v0/application-drafts` |
| Expected observable result | The named fixture is recorded in the composed draft request. |
| Tags | `smoke`, `performance`, `application`, `draft` |

## Update application draft: 05

| Metadata | Value |
| --- | --- |
| Actor | Provider |
| Inputs | `applicationDraft`, `fixtures/application/update-05.json` |
| Outputs | `applicationDraft` |
| HTTP interaction | `Local fixture composition for POST /api/v0/application-drafts` |
| Expected observable result | The named fixture is recorded in the composed draft request. |
| Tags | `smoke`, `performance`, `application`, `draft` |

## Update application draft: 06

| Metadata | Value |
| --- | --- |
| Actor | Provider |
| Inputs | `applicationDraft`, `fixtures/application/update-06.json` |
| Outputs | `applicationDraft` |
| HTTP interaction | `Local fixture composition for POST /api/v0/application-drafts` |
| Expected observable result | The named fixture is recorded in the composed draft request. |
| Tags | `smoke`, `performance`, `application`, `draft` |

## Update application draft: 07

| Metadata | Value |
| --- | --- |
| Actor | Provider |
| Inputs | `applicationDraft`, `fixtures/application/update-07.json` |
| Outputs | `applicationDraft` |
| HTTP interaction | `Local fixture composition for POST /api/v0/application-drafts` |
| Expected observable result | The named fixture is recorded in the composed draft request. |
| Tags | `smoke`, `performance`, `application`, `draft` |

## Update application draft: 08

| Metadata | Value |
| --- | --- |
| Actor | Provider |
| Inputs | `applicationDraft`, `fixtures/application/update-08.json` |
| Outputs | `applicationDraft` |
| HTTP interaction | `Local fixture composition for POST /api/v0/application-drafts` |
| Expected observable result | The named fixture is recorded in the composed draft request. |
| Tags | `smoke`, `performance`, `application`, `draft` |

## Update application draft: 09

| Metadata | Value |
| --- | --- |
| Actor | Provider |
| Inputs | `applicationDraft`, `fixtures/application/update-09.json` |
| Outputs | `applicationDraft` |
| HTTP interaction | `Local fixture composition for POST /api/v0/application-drafts` |
| Expected observable result | The named fixture is recorded in the composed draft request. |
| Tags | `smoke`, `performance`, `application`, `draft` |

## Update application draft: 10

| Metadata | Value |
| --- | --- |
| Actor | Provider |
| Inputs | `applicationDraft`, `fixtures/application/update-10.json` |
| Outputs | `applicationDraft` |
| HTTP interaction | `Local fixture composition for POST /api/v0/application-drafts` |
| Expected observable result | The named fixture is recorded in the composed draft request. |
| Tags | `smoke`, `performance`, `application`, `draft` |

## Update application draft: 11

| Metadata | Value |
| --- | --- |
| Actor | Provider |
| Inputs | `applicationDraft`, `fixtures/application/update-11.json` |
| Outputs | `applicationDraft` |
| HTTP interaction | `Local fixture composition for POST /api/v0/application-drafts` |
| Expected observable result | The named fixture is recorded in the composed draft request. |
| Tags | `smoke`, `performance`, `application`, `draft` |

## Update application draft: 12

| Metadata | Value |
| --- | --- |
| Actor | Provider |
| Inputs | `applicationDraft`, `fixtures/application/update-12.json` |
| Outputs | `applicationDraft` |
| HTTP interaction | `Local fixture composition for POST /api/v0/application-drafts` |
| Expected observable result | The named fixture is recorded in the composed draft request. |
| Tags | `smoke`, `performance`, `application`, `draft` |

## Update application draft: 13

| Metadata | Value |
| --- | --- |
| Actor | Provider |
| Inputs | `applicationDraft`, `fixtures/application/update-13.json` |
| Outputs | `applicationDraft` |
| HTTP interaction | `Local fixture composition for POST /api/v0/application-drafts` |
| Expected observable result | The named fixture is recorded in the composed draft request. |
| Tags | `smoke`, `performance`, `application`, `draft` |

## Update application draft: 14

| Metadata | Value |
| --- | --- |
| Actor | Provider |
| Inputs | `applicationDraft`, `fixtures/application/update-14.json` |
| Outputs | `applicationDraft` |
| HTTP interaction | `Local fixture composition for POST /api/v0/application-drafts` |
| Expected observable result | The named fixture is recorded in the composed draft request. |
| Tags | `smoke`, `performance`, `application`, `draft` |

## Update application draft: 15

| Metadata | Value |
| --- | --- |
| Actor | Provider |
| Inputs | `applicationDraft`, `fixtures/application/update-15.json` |
| Outputs | `applicationDraft` |
| HTTP interaction | `Local fixture composition for POST /api/v0/application-drafts` |
| Expected observable result | The named fixture is recorded in the composed draft request. |
| Tags | `smoke`, `performance`, `application`, `draft` |

## Upload application document

| Metadata | Value |
| --- | --- |
| Actor | Provider |
| Inputs | `applicationId`, `fixtures/documents/k6-evidence.pdf` |
| Outputs | `applicationDocumentId` |
| HTTP interaction | `POST /api/v0/applications/{id}/documents` |
| Expected observable result | Returns HTTP 201 and a document ID. |
| Tags | `smoke`, `performance`, `application`, `document` |

## Submit application draft

| Metadata | Value |
| --- | --- |
| Actor | Provider |
| Inputs | `applicationDraftId` |
| Outputs | `applicationId` |
| HTTP interaction | `POST /api/v0/application-drafts/{applicationId}/submit` |
| Expected observable result | Returns HTTP 200 or 202 and an Application ID. |
| Tags | `smoke`, `performance`, `application`, `submission` |

## Record manual-grant eligibility

| Metadata | Value |
| --- | --- |
| Actor | Provider/system |
| Inputs | `applicationId`, `fixtures/application/manual-grant-outcome.json` |
| Outputs | `applicationVersion` |
| HTTP interaction | `PATCH /api/v0/applications/{id}/auto-grant-outcome` |
| Expected observable result | Returns HTTP 200 or 204 with a MANUAL outcome. |
| Tags | `smoke`, `performance`, `application`, `work-queue` |

## Find application in Open Applications

| Metadata | Value |
| --- | --- |
| Actor | Application Caseworker |
| Inputs | `applicationId` |
| Outputs | `applicationWorkListItem` |
| HTTP interaction | `GET /api/v0/work-list?unassigned=true` |
| Expected observable result | The Application is visible as an unassigned Work Queue Item. |
| Tags | `smoke`, `contention`, `performance`, `work-queue` |

## Assign application from Work Queue

| Metadata | Value |
| --- | --- |
| Actor | Application Caseworker |
| Inputs | `applicationWorkListItem`, `fixtures/work-list/assign.json` |
| Outputs | `applicationAssignment` |
| HTTP interaction | `POST /api/v0/work-list/{itemId}/assign` |
| Expected observable result | Returns HTTP 200 for the expected assignment version. |
| Tags | `smoke`, `contention`, `performance`, `work-queue` |

## Find application in Personal Queue

| Metadata | Value |
| --- | --- |
| Actor | Application Caseworker |
| Inputs | `applicationId` |
| Outputs | `assignedApplicationWorkListItem` |
| HTTP interaction | `GET /api/v0/work-list?assignedToMe=true&unassigned=false` |
| Expected observable result | The Application is visible in the caseworker Personal Queue. |
| Tags | `smoke`, `performance`, `work-queue` |

## Grant application

| Metadata | Value |
| --- | --- |
| Actor | Application Caseworker |
| Inputs | `applicationId`, `fixtures/application/grant-decision.json` |
| Outputs | `grantedApplicationId` |
| HTTP interaction | `PATCH /api/v0/applications/{id}/decision` |
| Expected observable result | Returns HTTP 200 and removes the Application from the Work Queue. |
| Tags | `smoke`, `performance`, `application`, `decision` |

## List applications

| Metadata | Value |
| --- | --- |
| Actor | Provider |
| Inputs | `laaReference` |
| Outputs | `grantedApplicationId` |
| HTTP interaction | `GET /api/v0/applications` |
| Expected observable result | The granted parent Application is discoverable. |
| Tags | `smoke`, `performance`, `application` |

## Start Prior Authority draft

| Metadata | Value |
| --- | --- |
| Actor | Provider |
| Inputs | `grantedApplicationId`, `fixtures/prior-authority/create.json` |
| Outputs | `priorAuthorityId` |
| HTTP interaction | `POST /api/v0/prior-authorities` |
| Expected observable result | Returns HTTP 201 or 202 and a Prior Authority ID. |
| Tags | `smoke`, `performance`, `prior-authority`, `draft` |

## Update Prior Authority draft

| Metadata | Value |
| --- | --- |
| Actor | Provider |
| Inputs | `priorAuthorityId`, `fixtures/prior-authority/update.json` |
| Outputs | `priorAuthorityId` |
| HTTP interaction | `PUT /api/v0/prior-authorities/{priorAuthorityId}` |
| Expected observable result | Returns HTTP 204 after saving the required data. |
| Tags | `smoke`, `performance`, `prior-authority`, `draft` |

## Upload Prior Authority document

| Metadata | Value |
| --- | --- |
| Actor | Provider |
| Inputs | `priorAuthorityId`, `fixtures/documents/k6-evidence.pdf` |
| Outputs | `priorAuthorityDocumentId` |
| HTTP interaction | `POST /api/v0/prior-authorities/{priorAuthorityId}/documents` |
| Expected observable result | Returns HTTP 201 and a document ID. |
| Tags | `smoke`, `performance`, `prior-authority`, `document` |

## Submit Prior Authority

| Metadata | Value |
| --- | --- |
| Actor | Provider |
| Inputs | `priorAuthorityId` |
| Outputs | `submittedPriorAuthorityId` |
| HTTP interaction | `POST /api/v0/prior-authorities/{priorAuthorityId}/submit` |
| Expected observable result | Returns HTTP 200 or 202. |
| Tags | `smoke`, `performance`, `prior-authority`, `submission` |

## Find PA in Open Applications

| Metadata | Value |
| --- | --- |
| Actor | PA Caseworker |
| Inputs | `submittedPriorAuthorityId` |
| Outputs | `priorAuthorityWorkListItem` |
| HTTP interaction | `GET /api/v0/work-list?unassigned=true` |
| Expected observable result | The Prior Authority is visible as an unassigned Work Queue Item. |
| Tags | `smoke`, `performance`, `work-queue`, `prior-authority` |

## Assign PA from Work Queue

| Metadata | Value |
| --- | --- |
| Actor | PA Caseworker |
| Inputs | `priorAuthorityWorkListItem`, `fixtures/work-list/assign.json` |
| Outputs | `priorAuthorityAssignment` |
| HTTP interaction | `POST /api/v0/work-list/{itemId}/assign` |
| Expected observable result | Returns HTTP 200 for the expected assignment version. |
| Tags | `smoke`, `performance`, `work-queue`, `prior-authority` |

## Find PA in Personal Queue

| Metadata | Value |
| --- | --- |
| Actor | PA Caseworker |
| Inputs | `submittedPriorAuthorityId` |
| Outputs | `assignedPriorAuthorityWorkListItem` |
| HTTP interaction | `GET /api/v0/work-list?assignedToMe=true&unassigned=false` |
| Expected observable result | The Prior Authority is visible in the caseworker Personal Queue. |
| Tags | `smoke`, `performance`, `work-queue`, `prior-authority` |

## Grant PA

| Metadata | Value |
| --- | --- |
| Actor | PA Caseworker |
| Inputs | `submittedPriorAuthorityId`, `fixtures/prior-authority/grant-decision.json` |
| Outputs | `grantedPriorAuthorityId` |
| HTTP interaction | `PATCH /api/v0/prior-authorities/{priorAuthorityId}/decision` |
| Expected observable result | Returns HTTP 204 and removes the Prior Authority from the Work Queue. |
| Tags | `smoke`, `performance`, `prior-authority`, `decision` |


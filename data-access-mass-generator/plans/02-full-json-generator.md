# Plan: `FullJsonGenerator`

## Overview

Create a `FullJsonGenerator` in the `data-access-mass-generator` module that generates a fully-populated
`ApplicationContent` — covering every field and every level of nesting found in `application-example.json` —
using **datafaker** for all values. It follows the exact same `BaseGenerator` pattern as the existing
`ApplicationContentGenerator`.

---

## Design Principles

1. **One generator per nested object shape** — every distinct JSON object gets its own generator class.
2. **Every generator extends `BaseGenerator`** — including generators for objects that have no existing
   typed model in `data-access-service`. Those objects get new Lombok model classes created inside
   `data-access-mass-generator`.
3. **Reuse `BaseGenerator` from testUtilities, do not modify it** — all `Full*Generator` classes extend
   `uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator` which is already on the classpath via
   the `testUtilitiesRuntimeElements` dependency in `build.gradle`. No copy is needed and no changes are
   made to it.
4. **All generators are recreated from scratch in this module** — no existing generator class from
   testUtilities (e.g. `ApplicationContentGenerator`, `ProceedingGenerator`, `ApplicationOfficeGenerator`)
   is imported or reused. Every `Full*Generator` is a brand-new class in
   `uk.gov.justice.laa.dstew.access.massgenerator.generator.application`.
5. **New Lombok model classes for unmodelled objects** — objects with no existing typed model in
   `data-access-service/src/main` get new `@Builder(toBuilder = true) @Data @NoArgsConstructor @AllArgsConstructor`
   classes inside `data-access-mass-generator/src/main/java/.../massgenerator/model/`.
6. **Use datafaker for all values** — no hardcoded strings. Use the inherited `faker` instance and
   `getRandomDate()` where dates are needed.
7. **No model changes** — existing typed models (`ApplicationContent`, `ApplicationOffice`, `Proceeding`,
   `ApplicationMerits`) are used as-is. Extra JSON fields are stored via their existing
   `putAdditional*(key, value)` / `@JsonAnySetter` mechanism.
8. **Composition over inheritance** — sub-generators are instantiated as fields (e.g.
   `private final FullOfficeScheduleGenerator scheduleGenerator = new FullOfficeScheduleGenerator();`).
9. **No changes to existing generators** — `ApplicationContentGenerator` and all other testUtilities
   generators remain completely untouched.

---

## File Layout

```
data-access-mass-generator/src/main/java/
  uk/gov/justice/laa/dstew/access/massgenerator/
  │
  ├── generator/
  │   └── application/
  │       ├── FullJsonGenerator.java                        ← root
  │       ├── FullOfficeGenerator.java
  │       ├── FullOfficeScheduleGenerator.java
  │       ├── FullProviderGenerator.java
  │       ├── FullApplicantGenerator.java
  │       ├── FullApplicantAddressGenerator.java
  │ 
  │       ├── FullBenefitCheckResultGenerator.java
  │       ├── FullLegalFrameworkMeritsTaskListGenerator.java
  │       ├── FullStateMachineGenerator.java
  │       ├── FullMeansGenerator.java
  │       ├── FullCfeSubmissionGenerator.java
  │       ├── FullApplicationMeritsGenerator.java
  │       ├── FullOpponentDetailsGenerator.java
  │       ├── FullOpposableGenerator.java
  │       ├── FullProceedingGenerator.java
  │       ├── FullScopeLimitationGenerator.java
  │       └── FullProceedingMeritsGenerator.java
  │
  └── model/
      ├── FullOfficeSchedule.java
      ├── FullProvider.java
      ├── FullApplicant.java
      ├── FullApplicantAddress.java

      ├── FullBenefitCheckResult.java
      ├── FullLegalFrameworkMeritsTaskList.java
      ├── FullStateMachine.java
      ├── FullMeans.java
      ├── FullCfeSubmission.java
      ├── FullOpposable.java
      ├── FullOpponentDetails.java
      ├── FullScopeLimitation.java
      └── FullProceedingMerits.java
```

**17 generators + 13 new model classes. `BaseGenerator` reused from testUtilities classpath (unmodified).**

---

## Generator Specifications

Each generator extends `uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator` from testUtilities
(already on the classpath — no modification required). The constructor always passes `Model::toBuilder`
and `Model.ModelBuilder::build`. `createDefault()` uses the Lombok builder and the inherited `faker`
instance (seeded with `new java.util.Random(12345L)` by `BaseGenerator` — override with an unseeded
`faker` field if unique-per-call behaviour is needed).

All `Full*Generator` classes are **new files** in
`uk.gov.justice.laa.dstew.access.massgenerator.generator.application`. No generator from testUtilities
is imported or extended.

---

## Model Classes (in `massgenerator/model/`)

Each new model follows the same Lombok pattern as `ApplicationOffice`:

```java
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
public class FullXxx {
    private FieldType fieldName;
    // ...
}
```

### `FullOfficeSchedule`
| Field | Type |
|---|---|
| `id` | `String` |
| `officeId` | `String` |
| `areaOfLaw` | `String` |
| `categoryOfLaw` | `String` |
| `authorisationStatus` | `String` |
| `status` | `String` |
| `startDate` | `String` |
| `endDate` | `String` |
| `cancelled` | `Boolean` |
| `licenseIndicator` | `Integer` |
| `devolvedPowerStatus` | `String` |
| `createdAt` | `String` |
| `updatedAt` | `String` |

### `FullProvider`
| Field | Type |
|---|---|
| `id` | `String` |
| `username` | `String` |
| `type` | `String` |
| `roles` | `String` |
| `createdAt` | `String` |
| `updatedAt` | `String` |
| `officeCodes` | `String` |
| `firmId` | `String` |
| `selectedOfficeId` | `String` |
| `name` | `String` |
| `email` | `String` |
| `ccmsContactId` | `Long` |
| `silasId` | `String` |

### `FullApplicantAddress`
| Field | Type |
|---|---|
| `id` | `String` |
| `addressLineOne` | `String` |
| `addressLineTwo` | `String` |
| `addressLineThree` | `String` |
| `city` | `String` |
| `county` | `String` |
| `postcode` | `String` |
| `applicantId` | `String` |
| `createdAt` | `String` |
| `updatedAt` | `String` |
| `organisation` | `String` |
| `lookupUsed` | `Boolean` |
| `lookupId` | `String` |
| `buildingNumberName` | `String` |
| `location` | `String` |
| `countryCode` | `String` |
| `countryName` | `String` |
| `careOf` | `String` |
| `careOfFirstName` | `String` |
| `careOfLastName` | `String` |
| `careOfOrganisationName` | `String` |

### `FullApplicant`
| Field | Type |
|---|---|
| `id` | `String` |
| `firstName` | `String` |
| `lastName` | `String` |
| `dateOfBirth` | `String` |
| `createdAt` | `String` |
| `updatedAt` | `String` |
| `email` | `String` |
| `nationalInsuranceNumber` | `String` |
| `employed` | `Boolean` |
| `selfEmployed` | `Boolean` |
| `armedForces` | `Boolean` |
| `hasNationalInsuranceNumber` | `Boolean` |
| `ageForMeansTestPurposes` | `Integer` |
| `hasPartner` | `Boolean` |
| `receivesStateBenefits` | `Boolean` |
| `partnerHasContraryInterest` | `Boolean` |
| `studentFinance` | `Boolean` |
| `studentFinanceAmount` | `String` |
| `extraEmploymentInformation` | `Boolean` |
| `extraEmploymentInformationDetails` | `String` |
| `lastNameAtBirth` | `String` |
| `changedLastName` | `Boolean` |
| `sameCorrespondenceAndHomeAddress` | `Boolean` |
| `noFixedResidence` | `Boolean` |
| `correspondenceAddressChoice` | `String` |
| `sharedBenefitWithPartner` | `Boolean` |
| `appliedPreviously` | `Boolean` |
| `previousReference` | `String` |
| `relationshipToChildren` | `String` |
| `addresses` | `List<FullApplicantAddress>` |

### `FullBenefitCheckResult`
| Field | Type |
|---|---|
| `id` | `String` |
| `legalAidApplicationId` | `String` |
| `result` | `String` |
| `dwpRef` | `String` |
| `createdAt` | `String` |
| `updatedAt` | `String` |

### `FullLegalFrameworkMeritsTaskList`
| Field | Type |
|---|---|
| `id` | `String` |
| `legalAidApplicationId` | `String` |
| `serializedData` | `String` |
| `createdAt` | `String` |
| `updatedAt` | `String` |

### `FullStateMachine`
| Field | Type |
|---|---|
| `id` | `String` |
| `legalAidApplicationId` | `String` |
| `type` | `String` |
| `aasmState` | `String` |
| `createdAt` | `String` |
| `updatedAt` | `String` |
| `ccmsReason` | `String` |

### `FullCfeSubmission`
| Field | Type |
|---|---|
| `id` | `String` |
| `legalAidApplicationId` | `String` |
| `assessmentId` | `String` |
| `aasmState` | `String` |
| `errorMessage` | `String` |
| `cfeResult` | `String` |
| `createdAt` | `String` |
| `updatedAt` | `String` |

### `FullMeans`
| Field | Type |
|---|---|
| `openBanking` | `Map<String, Object>` |
| `otherAssetsDeclaration` | `String` |
| `savingsAmount` | `String` |
| `dependants` | `List<Object>` |
| `vehicles` | `List<Object>` |
| `capitalDisregards` | `List<Object>` |
| `legalAidApplicationTransactionTypes` | `List<Object>` |
| `regularTransactions` | `List<Object>` |
| `cashTransactions` | `List<Object>` |
| `mostRecentCFESubmission` | `FullCfeSubmission` |

### `FullOpposable`
| Field | Type |
|---|---|
| `id` | `String` |
| `createdAt` | `String` |
| `updatedAt` | `String` |
| `firstName` | `String` |
| `lastName` | `String` |

### `FullOpponentDetails`
| Field | Type |
|---|---|
| `id` | `String` |
| `legalAidApplicationId` | `String` |
| `createdAt` | `String` |
| `updatedAt` | `String` |
| `ccmsOpponentId` | `String` |
| `opposableType` | `String` |
| `opposableId` | `String` |
| `existsInCCMS` | `Boolean` |
| `opposable` | `FullOpposable` |

### `FullScopeLimitation`
| Field | Type |
|---|---|
| `id` | `String` |
| `scopeType` | `String` |
| `code` | `String` |
| `meaning` | `String` |
| `description` | `String` |
| `hearingDate` | `String` |
| `limitationNote` | `String` |
| `createdAt` | `String` |
| `updatedAt` | `String` |

### `FullProceedingMerits`
| Field | Type |
|---|---|
| `opponentsApplication` | `String` |
| `attemptsToSettle` | `String` |
| `specificIssue` | `String` |
| `varyOrder` | `String` |
| `chancesOfSuccess` | `String` |
| `prohibitedSteps` | `String` |
| `childCareAssessment` | `String` |
| `proceedingLinkedChildren` | `List<Object>` |
| `involvedChildren` | `List<Object>` |

---

## Generator Specifications

Each generator extends `uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator` from testUtilities.
The constructor always passes `Model::toBuilder` and `Model.ModelBuilder::build`.
`createDefault()` uses the Lombok builder and the inherited `faker` instance.

---

### 1. `FullOfficeScheduleGenerator`
`BaseGenerator<FullOfficeSchedule, FullOfficeSchedule.FullOfficeScheduleBuilder>`

| Field | Faker |
|---|---|
| `id` | `UUID.randomUUID().toString()` |
| `officeId` | `UUID.randomUUID().toString()` |
| `areaOfLaw` | `faker.options().option("LEGAL HELP", "FAMILY MEDIATION", "CRIME")` |
| `categoryOfLaw` | `faker.options().option("MAT", "CRM", "HOU")` |
| `authorisationStatus` | `faker.options().option("APPROVED", "PENDING", "SUSPENDED")` |
| `status` | `faker.options().option("Open", "Closed")` |
| `startDate` | `getRandomDate().toString()` |
| `endDate` | `LocalDate.of(2099, 12, 31).toString()` |
| `cancelled` | `faker.bool().bool()` |
| `licenseIndicator` | `faker.number().numberBetween(1, 5)` |
| `devolvedPowerStatus` | `faker.options().option("Yes - Excluding JR Proceedings", "No", "Yes")` |
| `createdAt` | `randomInstant()` |
| `updatedAt` | `randomInstant()` |

---

### 2. `FullOfficeGenerator`
`BaseGenerator<ApplicationOffice, ApplicationOffice.ApplicationOfficeBuilder>`

Uses existing `ApplicationOffice` model from `data-access-service`. Composes `FullOfficeScheduleGenerator`.

**Typed fields on `ApplicationOffice`:**
| Field | Faker |
|---|---|
| `code` | `faker.regexify("[0-9][A-Z][0-9]{3}[A-Z]")` |

**Additional fields via `putAdditionalProperty(key, value)`:**
| Key | Faker |
|---|---|
| `id` | `UUID.randomUUID().toString()` |
| `createdAt` | `randomInstant()` |
| `updatedAt` | `randomInstant()` |
| `ccmsId` | `faker.numerify("######")` |
| `firmId` | `UUID.randomUUID().toString()` |
| `schedules` | `List.of(scheduleGenerator.createDefault())` |

---

### 3. `FullProviderGenerator`
`BaseGenerator<FullProvider, FullProvider.FullProviderBuilder>`

| Field | Faker |
|---|---|
| `id` | `UUID.randomUUID().toString()` |
| `username` | `faker.regexify("[A-Z]{5}-[A-Z]{5}-[A-Z]{3}-LLP[0-9]")` |
| `type` | `null` |
| `roles` | `null` |
| `createdAt` | `randomInstant()` |
| `updatedAt` | `randomInstant()` |
| `officeCodes` | `faker.regexify("[0-9][A-Z][0-9]{3}[A-Z]:[0-9][A-Z][0-9]{3}[A-Z]")` |
| `firmId` | `UUID.randomUUID().toString()` |
| `selectedOfficeId` | `UUID.randomUUID().toString()` |
| `name` | `faker.name().fullName()` |
| `email` | `faker.internet().emailAddress()` |
| `ccmsContactId` | `faker.number().numberBetween(10000000L, 99999999L)` |
| `silasId` | `UUID.randomUUID().toString()` |

---

### 4. `FullApplicantAddressGenerator`
`BaseGenerator<FullApplicantAddress, FullApplicantAddress.FullApplicantAddressBuilder>`

| Field | Faker |
|---|---|
| `id` | `UUID.randomUUID().toString()` |
| `addressLineOne` | `faker.address().streetAddress()` |
| `addressLineTwo` | `faker.address().secondaryAddress()` |
| `addressLineThree` | `null` |
| `city` | `faker.address().city()` |
| `county` | `faker.address().county()` |
| `postcode` | `faker.regexify("[A-Z]{2}[0-9][A-Z] [0-9][A-Z]{2}")` |
| `applicantId` | `UUID.randomUUID().toString()` |
| `createdAt` | `randomInstant()` |
| `updatedAt` | `randomInstant()` |
| `organisation` | `null` |
| `lookupUsed` | `faker.bool().bool()` |
| `lookupId` | `null` |
| `buildingNumberName` | `faker.address().buildingNumber()` |
| `location` | `faker.options().option("home", "correspondence")` |
| `countryCode` | `"GBR"` |
| `countryName` | `"United Kingdom"` |
| `careOf` | `null` |
| `careOfFirstName` | `null` |
| `careOfLastName` | `null` |
| `careOfOrganisationName` | `null` |

---

### 5. `FullApplicantGenerator`
`BaseGenerator<FullApplicant, FullApplicant.FullApplicantBuilder>`

Composes `FullApplicantAddressGenerator`.

| Field | Faker |
|---|---|
| `id` | `UUID.randomUUID().toString()` |
| `firstName` | `faker.name().firstName()` |
| `lastName` | `faker.name().lastName()` |
| `dateOfBirth` | `getRandomDate().toString()` |
| `createdAt` | `randomInstant()` |
| `updatedAt` | `randomInstant()` |
| `email` | `faker.internet().emailAddress()` |
| `nationalInsuranceNumber` | `faker.regexify("[A-Z]{2}[0-9]{6}[A-Z]")` |
| `employed` | `null` |
| `selfEmployed` | `faker.bool().bool()` |
| `armedForces` | `faker.bool().bool()` |
| `hasNationalInsuranceNumber` | `faker.bool().bool()` |
| `ageForMeansTestPurposes` | `faker.number().numberBetween(0, 17)` |
| `hasPartner` | `faker.bool().bool()` |
| `receivesStateBenefits` | `null` |
| `partnerHasContraryInterest` | `null` |
| `studentFinance` | `null` |
| `studentFinanceAmount` | `null` |
| `extraEmploymentInformation` | `null` |
| `extraEmploymentInformationDetails` | `null` |
| `lastNameAtBirth` | `faker.name().lastName()` |
| `changedLastName` | `faker.bool().bool()` |
| `sameCorrespondenceAndHomeAddress` | `faker.bool().bool()` |
| `noFixedResidence` | `false` |
| `correspondenceAddressChoice` | `faker.options().option("home", "office")` |
| `sharedBenefitWithPartner` | `null` |
| `appliedPreviously` | `faker.bool().bool()` |
| `previousReference` | `null` |
| `relationshipToChildren` | `faker.options().option("father", "mother", "guardian")` |
| `addresses` | `List.of(addressGenerator.createDefault(), addressGenerator.createDefault())` |

---

### 6. `FullBenefitCheckResultGenerator`
`BaseGenerator<FullBenefitCheckResult, FullBenefitCheckResult.FullBenefitCheckResultBuilder>`

| Field | Faker |
|---|---|
| `id` | `UUID.randomUUID().toString()` |
| `legalAidApplicationId` | `UUID.randomUUID().toString()` |
| `result` | `faker.options().option("skipped:no_means_test_required", "no", "yes")` |
| `dwpRef` | `null` |
| `createdAt` | `randomInstant()` |
| `updatedAt` | `randomInstant()` |

---

### 7. `FullLegalFrameworkMeritsTaskListGenerator`
`BaseGenerator<FullLegalFrameworkMeritsTaskList, FullLegalFrameworkMeritsTaskList.FullLegalFrameworkMeritsTaskListBuilder>`

| Field | Value |
|---|---|
| `id` | `UUID.randomUUID().toString()` |
| `legalAidApplicationId` | `UUID.randomUUID().toString()` |
| `serializedData` | Fixed YAML constant (opaque Ruby-serialised string from `application-example.json`) |
| `createdAt` | `randomInstant()` |
| `updatedAt` | `randomInstant()` |

---

### 8. `FullStateMachineGenerator`
`BaseGenerator<FullStateMachine, FullStateMachine.FullStateMachineBuilder>`

| Field | Faker |
|---|---|
| `id` | `UUID.randomUUID().toString()` |
| `legalAidApplicationId` | `UUID.randomUUID().toString()` |
| `type` | `faker.options().option("SpecialChildrenActStateMachine", "MeritsStateMachine")` |
| `aasmState` | `faker.options().option("generating_reports", "submitted", "draft", "checking_merits_answers")` |
| `createdAt` | `randomInstant()` |
| `updatedAt` | `randomInstant()` |
| `ccmsReason` | `null` |

---

### 9. `FullCfeSubmissionGenerator`
`BaseGenerator<FullCfeSubmission, FullCfeSubmission.FullCfeSubmissionBuilder>`

| Field | Faker |
|---|---|
| `id` | `UUID.randomUUID().toString()` |
| `legalAidApplicationId` | `UUID.randomUUID().toString()` |
| `assessmentId` | `null` |
| `aasmState` | `faker.options().option("cfe_not_called", "complete", "failed")` |
| `errorMessage` | `null` |
| `cfeResult` | `null` |
| `createdAt` | `randomInstant()` |
| `updatedAt` | `randomInstant()` |

---

### 10. `FullMeansGenerator`
`BaseGenerator<FullMeans, FullMeans.FullMeansBuilder>`

Composes `FullCfeSubmissionGenerator`.

| Field | Value |
|---|---|
| `openBanking` | `Map.of("bankProviders", List.of())` |
| `otherAssetsDeclaration` | `null` |
| `savingsAmount` | `null` |
| `dependants` | `List.of()` |
| `vehicles` | `List.of()` |
| `capitalDisregards` | `List.of()` |
| `legalAidApplicationTransactionTypes` | `List.of()` |
| `regularTransactions` | `List.of()` |
| `cashTransactions` | `List.of()` |
| `mostRecentCFESubmission` | `cfeSubmissionGenerator.createDefault()` |

---

### 11. `FullOpposableGenerator`
`BaseGenerator<FullOpposable, FullOpposable.FullOpposableBuilder>`

| Field | Faker |
|---|---|
| `id` | `UUID.randomUUID().toString()` |
| `createdAt` | `randomInstant()` |
| `updatedAt` | `randomInstant()` |
| `firstName` | `faker.name().firstName()` |
| `lastName` | `faker.name().lastName()` |

---

### 12. `FullOpponentDetailsGenerator`
`BaseGenerator<FullOpponentDetails, FullOpponentDetails.FullOpponentDetailsBuilder>`

Composes `FullOpposableGenerator`.

| Field | Faker |
|---|---|
| `id` | `UUID.randomUUID().toString()` |
| `legalAidApplicationId` | `UUID.randomUUID().toString()` |
| `createdAt` | `randomInstant()` |
| `updatedAt` | `randomInstant()` |
| `ccmsOpponentId` | `null` |
| `opposableType` | `faker.options().option("ApplicationMeritsTask::Individual", "ApplicationMeritsTask::Organisation")` |
| `opposableId` | `UUID.randomUUID().toString()` |
| `existsInCCMS` | `faker.bool().bool()` |
| `opposable` | `opposableGenerator.createDefault()` |

---

### 13. `FullApplicationMeritsGenerator`
`BaseGenerator<ApplicationMerits, ApplicationMerits.ApplicationMeritsBuilder>`

Uses existing `ApplicationMerits` model from `data-access-service`. Composes `FullOpponentDetailsGenerator`.

**Note:** The full JSON opponent shape is richer than the typed `OpponentDetails` model, so the opponents
list is passed via `putAdditionalContent("opponents", ...)` and the typed `opponents` field is left empty.

**Typed fields:**
| Field | Value |
|---|---|
| `involvedChildren` | `List.of()` |

**Additional fields via `putAdditionalContent(key, value)`:**
| Key | Value |
|---|---|
| `opponents` | `List.of(opponentDetailsGenerator.createDefault())` |
| `statementOfCase` | `null` |
| `domesticAbuseSummary` | `null` |
| `partiesMentalCapacity` | `null` |
| `latestIncident` | `null` |
| `allegation` | `null` |
| `undertaking` | `null` |
| `urgency` | `null` |
| `appeal` | `null` |
| `matterOpposition` | `null` |

---

### 14. `FullScopeLimitationGenerator`
`BaseGenerator<FullScopeLimitation, FullScopeLimitation.FullScopeLimitationBuilder>`

| Field | Faker |
|---|---|
| `id` | `UUID.randomUUID().toString()` |
| `scopeType` | `faker.options().option("substantive", "emergency")` |
| `code` | `faker.regexify("[A-Z]{2}[0-9]{3}")` |
| `meaning` | `faker.options().option("Final hearing", "All steps", "Hearing")` |
| `description` | `faker.lorem().sentence()` |
| `hearingDate` | `null` |
| `limitationNote` | `null` |
| `createdAt` | `randomInstant()` |
| `updatedAt` | `randomInstant()` |

---

### 15. `FullProceedingGenerator`
`BaseGenerator<Proceeding, Proceeding.ProceedingBuilder>`

Uses existing `Proceeding` model from `data-access-service`. Composes `FullScopeLimitationGenerator`.

**Typed fields on `Proceeding`:**
| Field | Faker |
|---|---|
| `id` | `UUID.randomUUID()` |
| `categoryOfLaw` | `faker.options().option("Family", "Crime", "Housing")` |
| `matterType` | `faker.options().option("SCA", "special children act (SCA)")` |
| `leadProceeding` | `faker.bool().bool()` |
| `usedDelegatedFunctions` | `faker.bool().bool()` |
| `description` | `faker.lorem().sentence()` |
| `meaning` | `faker.options().option("Care order", "Parental responsibility", "Emergency protection order - discharge")` |
| `usedDelegatedFunctionsOn` | `getRandomDate()` |
| `substantiveCostLimitation` | `faker.number().numberBetween(1000, 50000) + ".0"` |
| `substantiveLevelOfServiceName` | `faker.options().option("Full Representation", "Limited Case Work")` |

**Additional fields via `putAdditionalProperty(key, value)`:**
| Key | Faker |
|---|---|
| `legalAidApplicationId` | `UUID.randomUUID().toString()` |
| `proceedingCaseId` | `faker.number().numberBetween(50000000, 59999999)` |
| `delegatedFunctionsCostLimitation` | `"2250.0"` |
| `usedDelegatedFunctionsReportedOn` | `getRandomDate().toString()` |
| `createdAt` | `randomInstant()` |
| `updatedAt` | `randomInstant()` |
| `name` | `faker.options().option("app_for_care_order_sca", "parental_responsibility_sca", "app_discharge_emergency_sca")` |
| `categoryLawCode` | `faker.options().option("MAT", "CRM")` |
| `ccmsCode` | `faker.regexify("PB[0-9]{3}")` |
| `ccmsMatterCode` | `faker.regexify("[A-Z]{5}")` |
| `clientInvolvementTypeCCMSCode` | `faker.options().option("W", "D", "A")` |
| `clientInvolvementTypeDescription` | `faker.options().option("A child subject of the proceeding", "Defendant", "Applicant")` |
| `emergencyLevelOfService` | `null` |
| `emergencyLevelOfServiceName` | `null` |
| `emergencyLevelOfServiceStage` | `null` |
| `substantiveLevelOfService` | `faker.number().numberBetween(1, 5)` |
| `substantiveLevelOfServiceStage` | `faker.number().numberBetween(1, 10)` |
| `acceptedEmergencyDefaults` | `null` |
| `acceptedSubstantiveDefaults` | `faker.bool().bool()` |
| `scaType` | `faker.options().option("core", "related")` |
| `relatedOrders` | `List.of()` |
| `finalHearings` | `List.of()` |
| `scopeLimitations` | `List.of(scopeLimitationGenerator.createDefault())` |

---

### 16. `FullProceedingMeritsGenerator`
`BaseGenerator<FullProceedingMerits, FullProceedingMerits.FullProceedingMeritsBuilder>`

| Field | Value |
|---|---|
| `opponentsApplication` | `null` |
| `attemptsToSettle` | `null` |
| `specificIssue` | `null` |
| `varyOrder` | `null` |
| `chancesOfSuccess` | `null` |
| `prohibitedSteps` | `null` |
| `childCareAssessment` | `null` |
| `proceedingLinkedChildren` | `List.of()` |
| `involvedChildren` | `List.of()` |

---

### 17. `FullJsonGenerator`
`BaseGenerator<ApplicationContent, ApplicationContent.ApplicationContentBuilder>`

Uses existing `ApplicationContent` model from `data-access-service`. Composes all other generators.

**Composed sub-generators (instantiated as fields):**
- `FullOfficeGenerator`
- `FullProviderGenerator`
- `FullApplicantGenerator`
- `FullBenefitCheckResultGenerator`
- `FullLegalFrameworkMeritsTaskListGenerator`
- `FullStateMachineGenerator`
- `FullMeansGenerator`
- `FullApplicationMeritsGenerator`
- `FullProceedingGenerator`
- `FullProceedingMeritsGenerator`

**Typed fields on `ApplicationContent`:**
| Field | Faker |
|---|---|
| `id` | `UUID.randomUUID()` |
| `submittedAt` | `randomInstant()` |
| `status` | `faker.options().option("generating_reports", "submitted", "draft")` |
| `laaReference` | `faker.regexify("L-[A-Z]{3}-[A-Z][0-9]{2}-[0-9]")` |
| `lastNameAtBirth` | `faker.name().lastName()` |
| `previousApplicationReference` | `faker.regexify("[A-Z]{2}[0-9]{3}[A-Z]")` |
| `relationshipToChildren` | `faker.options().option("father", "mother", "guardian")` |
| `correspondenceAddressType` | `faker.options().option("Home", "office")` |
| `office` | `officeGenerator.createDefault()` |
| `applicationMerits` | `applicationMeritsGenerator.createDefault()` |
| `proceedings` | `List.of(proceedingGenerator.createDefault(), proceedingGenerator.createDefault(), proceedingGenerator.createDefault())` |
| `submitterEmail` | `faker.internet().emailAddress()` |

**Additional top-level fields via `putAdditionalApplicationContent(key, value)`:**
| Key | Faker / Value |
|---|---|
| `applicationRef` | `faker.regexify("L-[A-Z]{3}-[A-Z][0-9]{2}-[0-9]")` |
| `createdAt` | `randomInstant()` |
| `updatedAt` | `randomInstant()` |
| `applicantId` | `UUID.randomUUID().toString()` |
| `hasOfflineAccounts` | `null` |
| `openBankingConsent` | `null` |
| `openBankingConsentChoiceAt` | `null` |
| `ownHome` | `null` |
| `propertyValue` | `null` |
| `sharedOwnership` | `null` |
| `outstandingMortgageAmount` | `null` |
| `percentageHome` | `null` |
| `providerStep` | `faker.options().option("submitted_applications", "provider_details")` |
| `providerId` | `UUID.randomUUID().toString()` |
| `draft` | `false` |
| `transactionPeriodStartOn` | `null` |
| `transactionPeriodFinishOn` | `null` |
| `transactionsGathered` | `null` |
| `completedAt` | `null` |
| `declarationAcceptedAt` | `null` |
| `providerStepParams` | `Map.of()` |
| `ownVehicle` | `null` |
| `substantiveApplicationDeadlineOn` | `null` |
| `substantiveApplication` | `null` |
| `hasDependants` | `null` |
| `officeId` | `UUID.randomUUID().toString()` |
| `hasRestrictions` | `null` |
| `restrictionsDetails` | `null` |
| `noCreditTransactionTypesSelected` | `null` |
| `noDebitTransactionTypesSelected` | `null` |
| `providerReceivedCitizenConsent` | `null` |
| `discardedAt` | `null` |
| `inScopeOfLaspo` | `null` |
| `emergencyCostOverride` | `null` |
| `emergencyCostRequested` | `null` |
| `emergencyCostReasons` | `null` |
| `noCashIncome` | `null` |
| `noCashOutgoings` | `null` |
| `purgeableOn` | `null` |
| `allowedDocumentCategories` | `List.of()` |
| `extraEmploymentInformation` | `null` |
| `extraEmploymentInformationDetails` | `null` |
| `fullEmploymentDetails` | `null` |
| `clientDeclarationConfirmedAt` | `randomInstant()` |
| `substantiveCostOverride` | `null` |
| `substantiveCostRequested` | `null` |
| `substantiveCostReasons` | `null` |
| `applicantInReceiptOfHousingBenefit` | `null` |
| `copyCase` | `faker.bool().bool()` |
| `copyCaseId` | `UUID.randomUUID().toString()` |
| `caseCloned` | `faker.bool().bool()` |
| `separateRepresentationRequired` | `faker.bool().bool()` |
| `plfCourtOrder` | `null` |
| `reviewed` | `Map.of("checkProviderAnswers", Map.of("status", "completed", "at", randomInstant()))` |
| `dwpResultConfirmed` | `faker.bool().bool()` |
| `linkedApplicationCompleted` | `faker.bool().bool()` |
| `autoGrant` | `false` |
| `provider` | `providerGenerator.createDefault()` |
| `applicant` | `applicantGenerator.createDefault()` |
| `partner` | `null` |
| `allLinkedApplications` | `List.of()` |
| `benefitCheckResult` | `benefitCheckResultGenerator.createDefault()` |
| `dwpOverride` | `null` |
| `legalFrameworkMeritsTaskList` | `legalFrameworkMeritsTaskListGenerator.createDefault()` |
| `stateMachine` | `stateMachineGenerator.createDefault()` |
| `hmrcResponses` | `List.of()` |
| `employments` | `List.of()` |
| `means` | `meansGenerator.createDefault()` |
| `proceedingMerits` | `List.of(proceedingMeritsGenerator.createDefault(), proceedingMeritsGenerator.createDefault(), proceedingMeritsGenerator.createDefault())` |

---

## Helper: `randomInstant()`

A private helper method inlined in each generator that needs it:

```java
private String randomInstant() {
    return Instant.now().minus(faker.number().numberBetween(0, 365), ChronoUnit.DAYS).toString();
}
```

---

No other changes to `MassDataGeneratorRunner` or `PersistedDataGenerator` are needed.


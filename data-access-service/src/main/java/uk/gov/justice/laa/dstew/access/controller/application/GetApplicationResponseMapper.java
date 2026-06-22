package uk.gov.justice.laa.dstew.access.controller.application;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.dstew.access.domain.ApplicationProceedingDomain;
import uk.gov.justice.laa.dstew.access.domain.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.domain.InvolvedChildDomain;
import uk.gov.justice.laa.dstew.access.domain.OpponentDomain;
import uk.gov.justice.laa.dstew.access.domain.ProviderDomain;
import uk.gov.justice.laa.dstew.access.domain.ScopeLimitationDomain;
import uk.gov.justice.laa.dstew.access.model.ApplicationProceedingResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationType;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.InvolvedChildResponse;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;
import uk.gov.justice.laa.dstew.access.model.OpponentResponse;
import uk.gov.justice.laa.dstew.access.model.ProviderResponse;
import uk.gov.justice.laa.dstew.access.model.ScopeLimitationResponse;
import uk.gov.justice.laa.dstew.access.utils.EnumParsingUtils;

/** Maps get-application read models to API responses. */
public class GetApplicationResponseMapper {

  /**
   * Converts a read model to a response entity.
   *
   * @param applicationReadModel application read model
   * @return response entity containing application response
   */
  public ResponseEntity<ApplicationResponse> toGetApplicationResponse(
      ApplicationReadModel applicationReadModel) {
    ApplicationResponse applicationResponse = new ApplicationResponse();
    applicationResponse.setApplicationId(applicationReadModel.id());
    applicationResponse.setStatus(
        applicationReadModel.status() != null
            ? ApplicationStatus.valueOf(applicationReadModel.status())
            : null);
    applicationResponse.setLaaReference(applicationReadModel.laaReference());
    applicationResponse.setLastUpdated(
        OffsetDateTime.ofInstant(applicationReadModel.updatedAt(), ZoneOffset.UTC));
    applicationResponse.setAssignedTo(applicationReadModel.caseworkerId());
    applicationResponse.setSubmittedAt(
        applicationReadModel.submittedAt() != null
            ? OffsetDateTime.ofInstant(applicationReadModel.submittedAt(), ZoneOffset.UTC)
            : null);
    applicationResponse.setIsLead(applicationReadModel.isLead());
    applicationResponse.setUsedDelegatedFunctions(applicationReadModel.usedDelegatedFunctions());
    applicationResponse.setAutoGrant(applicationReadModel.autoGrant());
    applicationResponse.setDecisionStatus(
        applicationReadModel.decisionStatus() != null
            ? DecisionStatus.valueOf(applicationReadModel.decisionStatus())
            : null);
    applicationResponse.setApplicationType(
        ApplicationType.valueOf(applicationReadModel.applicationType()));
    applicationResponse.setOpponents(toOpponentResponses(applicationReadModel.opponents()));
    applicationResponse.setProvider(toProviderResponse(applicationReadModel.provider()));
    applicationResponse.setProceedings(
        toApplicationProceedingResponses(applicationReadModel.proceedings()));
    applicationResponse.setVersion(applicationReadModel.version());
    return ResponseEntity.ok(applicationResponse);
  }

  private List<OpponentResponse> toOpponentResponses(List<OpponentDomain> opponentDomains) {
    if (opponentDomains == null) {
      return Collections.emptyList();
    }

    return opponentDomains.stream()
        .map(
            opponent ->
                OpponentResponse.builder()
                    .opponentType(opponent.opponentType())
                    .firstName(opponent.firstName())
                    .lastName(opponent.lastName())
                    .organisationName(opponent.organisationName())
                    .build())
        .toList();
  }

  private ProviderResponse toProviderResponse(ProviderDomain providerDomain) {
    if (providerDomain == null) {
      return null;
    }

    ProviderResponse response = new ProviderResponse();
    response.setOfficeCode(providerDomain.officeCode());
    response.setContactEmail(providerDomain.contactEmail());
    return response;
  }

  private List<ApplicationProceedingResponse> toApplicationProceedingResponses(
      List<ApplicationProceedingDomain> applicationProceedingDomains) {
    if (applicationProceedingDomains == null) {
      return Collections.emptyList();
    }

    return applicationProceedingDomains.stream()
        .map(this::toApplicationProceedingResponse)
        .toList();
  }

  private ApplicationProceedingResponse toApplicationProceedingResponse(
      ApplicationProceedingDomain applicationProceedingDomain) {
    return ApplicationProceedingResponse.builder()
        .proceedingId(applicationProceedingDomain.proceedingId())
        .proceedingDescription(applicationProceedingDomain.description())
        .proceedingType(applicationProceedingDomain.proceedingType())
        .categoryOfLaw(
            EnumParsingUtils.convertToCategoryOfLaw(applicationProceedingDomain.categoryOfLaw()))
        .matterType(EnumParsingUtils.convertToMatterType(applicationProceedingDomain.matterType()))
        .levelOfService(applicationProceedingDomain.levelOfService())
        .substantiveCostLimitation(applicationProceedingDomain.substantiveCostLimitation())
        .delegatedFunctionsDate(applicationProceedingDomain.delegatedFunctionsDate())
        .meritsDecision(
            applicationProceedingDomain.meritsDecision() != null
                ? MeritsDecisionStatus.valueOf(applicationProceedingDomain.meritsDecision())
                : null)
        .involvedChildren(toInvolvedChildResponses(applicationProceedingDomain.involvedChildren()))
        .scopeLimitations(
            toScopeLimitationResponses(applicationProceedingDomain.scopeLimitations()))
        .build();
  }

  private List<InvolvedChildResponse> toInvolvedChildResponses(
      List<InvolvedChildDomain> involvedChildDomains) {
    if (involvedChildDomains == null) {
      return Collections.emptyList();
    }

    return involvedChildDomains.stream()
        .map(
            child ->
                new InvolvedChildResponse()
                    .fullName(child.fullName())
                    .dateOfBirth(child.dateOfBirth()))
        .toList();
  }

  private List<ScopeLimitationResponse> toScopeLimitationResponses(
      List<ScopeLimitationDomain> scopeLimitationDomains) {
    if (scopeLimitationDomains == null) {
      return Collections.emptyList();
    }

    return scopeLimitationDomains.stream()
        .map(
            scopeLimitation ->
                ScopeLimitationResponse.builder()
                    .scopeLimitation(scopeLimitation.scopeLimitation())
                    .scopeDescription(scopeLimitation.scopeDescription())
                    .build())
        .toList();
  }
}

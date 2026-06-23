package uk.gov.justice.laa.dstew.access.controller.application;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import org.springframework.http.ResponseEntity;
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
import uk.gov.justice.laa.dstew.access.usecase.getapplication.model.ApplicationProceedingReadModel;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.model.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.model.InvolvedChildReadModel;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.model.OpponentReadModel;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.model.ProviderReadModel;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.model.ScopeLimitationReadModel;
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

  private List<OpponentResponse> toOpponentResponses(List<OpponentReadModel> opponentReadModels) {
    if (opponentReadModels == null) {
      return Collections.emptyList();
    }

    return opponentReadModels.stream()
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

  private ProviderResponse toProviderResponse(ProviderReadModel providerReadModel) {
    if (providerReadModel == null) {
      return null;
    }

    ProviderResponse response = new ProviderResponse();
    response.setOfficeCode(providerReadModel.officeCode());
    response.setContactEmail(providerReadModel.contactEmail());
    return response;
  }

  private List<ApplicationProceedingResponse> toApplicationProceedingResponses(
      List<ApplicationProceedingReadModel> applicationProceedingReadModels) {
    if (applicationProceedingReadModels == null) {
      return Collections.emptyList();
    }

    return applicationProceedingReadModels.stream()
        .map(this::toApplicationProceedingResponse)
        .toList();
  }

  private ApplicationProceedingResponse toApplicationProceedingResponse(
      ApplicationProceedingReadModel applicationProceedingReadModel) {
    return ApplicationProceedingResponse.builder()
        .proceedingId(applicationProceedingReadModel.proceedingId())
        .proceedingDescription(applicationProceedingReadModel.description())
        .proceedingType(applicationProceedingReadModel.proceedingType())
        .categoryOfLaw(
            EnumParsingUtils.convertToCategoryOfLaw(applicationProceedingReadModel.categoryOfLaw()))
        .matterType(
            EnumParsingUtils.convertToMatterType(applicationProceedingReadModel.matterType()))
        .levelOfService(applicationProceedingReadModel.levelOfService())
        .substantiveCostLimitation(applicationProceedingReadModel.substantiveCostLimitation())
        .delegatedFunctionsDate(applicationProceedingReadModel.delegatedFunctionsDate())
        .meritsDecision(
            applicationProceedingReadModel.meritsDecision() != null
                ? MeritsDecisionStatus.valueOf(applicationProceedingReadModel.meritsDecision())
                : null)
        .involvedChildren(
            toInvolvedChildResponses(applicationProceedingReadModel.involvedChildren()))
        .scopeLimitations(
            toScopeLimitationResponses(applicationProceedingReadModel.scopeLimitations()))
        .build();
  }

  private List<InvolvedChildResponse> toInvolvedChildResponses(
      List<InvolvedChildReadModel> involvedChildReadModels) {
    if (involvedChildReadModels == null) {
      return Collections.emptyList();
    }

    return involvedChildReadModels.stream()
        .map(
            child ->
                new InvolvedChildResponse()
                    .fullName(child.fullName())
                    .dateOfBirth(child.dateOfBirth()))
        .toList();
  }

  private List<ScopeLimitationResponse> toScopeLimitationResponses(
      List<ScopeLimitationReadModel> scopeLimitationReadModels) {
    if (scopeLimitationReadModels == null) {
      return Collections.emptyList();
    }

    return scopeLimitationReadModels.stream()
        .map(
            scopeLimitation ->
                ScopeLimitationResponse.builder()
                    .scopeLimitation(scopeLimitation.scopeLimitation())
                    .scopeDescription(scopeLimitation.scopeDescription())
                    .build())
        .toList();
  }
}

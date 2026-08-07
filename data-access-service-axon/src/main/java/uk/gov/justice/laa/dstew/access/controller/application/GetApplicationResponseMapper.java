package uk.gov.justice.laa.dstew.access.controller.application;

import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContent;
import uk.gov.justice.laa.dstew.access.applicationcontent.Opponent;
import uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding;
import uk.gov.justice.laa.dstew.access.applicationcontent.ScopeLimitation;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationProceeding;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationMeritsDecision;
import uk.gov.justice.laa.dstew.access.model.ApplicationProceedingResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.InvolvedChildResponse;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;
import uk.gov.justice.laa.dstew.access.model.OpponentResponse;
import uk.gov.justice.laa.dstew.access.model.ProviderResponse;
import uk.gov.justice.laa.dstew.access.model.ScopeLimitationResponse;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;

/** Maps the typed current-state projection to the public application response. */
@Component
public class GetApplicationResponseMapper {

  /** Builds a response without reparsing content from JSON. */
  public ApplicationResponse toResponse(ApplicationReadModel application) {
    ApplicationContent content = application.getApplicationContent();
    ApplicationResponse response = new ApplicationResponse();
    response.setApplicationId(application.getApplicationId());
    response.setStatus(ApplicationStatus.valueOf(application.getStatus()));
    response.setLaaReference(application.getLaaReference());
    response.setLastUpdated(application.getModifiedAt().atOffset(ZoneOffset.UTC));
    response.setSubmittedAt(
        application.getSubmittedAt() == null
            ? null
            : application.getSubmittedAt().atOffset(ZoneOffset.UTC));
    response.setIsLead(application.getLeadApplicationId() == null);
    response.setAssignedTo(application.getCaseworkerId());
    response.setUsedDelegatedFunctions(application.getUsedDelegatedFunctions());
    response.setAutoGrant(application.getAutoGranted());
    response.setDecisionStatus(
        application.getDecisionStatus() == null
            ? null
            : uk.gov.justice.laa.dstew.access.model.DecisionStatus.valueOf(
                application.getDecisionStatus()));
    response.setVersion(application.getApplicationVersion());
    response.setProvider(toProvider(application, content));
    response.setOpponents(toOpponents(content));
    response.setProceedings(
        toProceedings(application.getProceedings(), content, application.getMeritsDecisions()));
    return response;
  }

  private ProviderResponse toProvider(
      ApplicationReadModel application, ApplicationContent content) {
    String contactEmail = null;
    if (content != null && content.getProvider() != null) {
      contactEmail = content.getProvider().getContactEmail();
    }
    if (application.getOfficeCode() == null && contactEmail == null) {
      return null;
    }
    return ProviderResponse.builder()
        .officeCode(application.getOfficeCode())
        .contactEmail(contactEmail)
        .build();
  }

  private List<OpponentResponse> toOpponents(ApplicationContent content) {
    if (content == null || content.getOpponents() == null) {
      return Collections.emptyList();
    }
    return content.getOpponents().stream().map(this::toOpponent).toList();
  }

  private OpponentResponse toOpponent(Opponent opponent) {
    return OpponentResponse.builder()
        .opponentType(opponent.getOpponentType())
        .firstName(opponent.getFirstName())
        .lastName(opponent.getLastName())
        .organisationName(opponent.getOrganisationName())
        .build();
  }

  private List<ApplicationProceedingResponse> toProceedings(
      List<ApplicationProceeding> proceedings,
      ApplicationContent content,
      Map<UUID, ApplicationMeritsDecision> meritsDecisions) {
    if (proceedings == null) {
      return Collections.emptyList();
    }
    return proceedings.stream()
        .map(proceeding -> toProceeding(proceeding, content, meritsDecisions))
        .toList();
  }

  private ApplicationProceedingResponse toProceeding(
      ApplicationProceeding applicationProceeding,
      ApplicationContent content,
      Map<UUID, ApplicationMeritsDecision> meritsDecisions) {
    Proceeding proceeding = applicationProceeding.proceedingContent();
    ApplicationMeritsDecision meritsDecision =
        meritsDecisions == null ? null : meritsDecisions.get(applicationProceeding.proceedingId());
    return ApplicationProceedingResponse.builder()
        .proceedingId(applicationProceeding.proceedingId())
        .proceedingDescription(applicationProceeding.description())
        .proceedingType(proceeding.getMeaning())
        .delegatedFunctionsDate(proceeding.getDelegatedFunctionsDate())
        .categoryOfLaw(toCategoryOfLaw(proceeding.getCategoryOfLaw()))
        .matterType(toMatterType(proceeding.getMatterType()))
        .levelOfService(proceeding.getSubstantiveLevelOfServiceName())
        .substantiveCostLimitation(
            proceeding.getSubstantiveCostLimitation() == null
                ? null
                : proceeding.getSubstantiveCostLimitation().doubleValue())
        .meritsDecision(
            meritsDecision == null || meritsDecision.decision() == null
                ? null
                : MeritsDecisionStatus.valueOf(meritsDecision.decision()))
        .scopeLimitations(toScopeLimitations(proceeding.getScopeLimitations()))
        .involvedChildren(toInvolvedChildren(proceeding))
        .build();
  }

  private CategoryOfLaw toCategoryOfLaw(String categoryOfLaw) {
    if (categoryOfLaw == null) {
      return null;
    }
    try {
      return CategoryOfLaw.valueOf(categoryOfLaw.toUpperCase().replace(" ", "_"));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private MatterType toMatterType(String matterType) {
    if (matterType == null) {
      return null;
    }
    try {
      return MatterType.valueOf(matterType.toUpperCase().replace(" ", "_"));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private List<ScopeLimitationResponse> toScopeLimitations(List<ScopeLimitation> scopeLimitations) {
    if (scopeLimitations == null) {
      return Collections.emptyList();
    }
    return scopeLimitations.stream()
        .map(
            scopeLimitation ->
                ScopeLimitationResponse.builder()
                    .scopeLimitation(scopeLimitation.getMeaning())
                    .scopeDescription(scopeLimitation.getDescription())
                    .build())
        .toList();
  }

  private List<InvolvedChildResponse> toInvolvedChildren(Proceeding proceeding) {
    if (proceeding.getInvolvedChildren() == null) {
      return Collections.emptyList();
    }
    return proceeding.getInvolvedChildren().stream()
        .map(
            child ->
                new InvolvedChildResponse()
                    .fullName(child.getFullName())
                    .dateOfBirth(child.getDateOfBirth()))
        .toList();
  }
}

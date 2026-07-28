package uk.gov.justice.laa.dstew.access.controller.synchronousapplication;

import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContent;
import uk.gov.justice.laa.dstew.access.applicationcontent.InvolvedChild;
import uk.gov.justice.laa.dstew.access.applicationcontent.OpponentDetails;
import uk.gov.justice.laa.dstew.access.applicationcontent.Opposable;
import uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding;
import uk.gov.justice.laa.dstew.access.applicationcontent.ProceedingLinkedChild;
import uk.gov.justice.laa.dstew.access.applicationcontent.ProceedingMerits;
import uk.gov.justice.laa.dstew.access.command.synchronousapplication.SynchronousApplicationProceeding;
import uk.gov.justice.laa.dstew.access.model.ApplicationProceedingResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationType;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.InvolvedChildResponse;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.model.OpponentResponse;
import uk.gov.justice.laa.dstew.access.model.ProviderResponse;
import uk.gov.justice.laa.dstew.access.model.ScopeLimitationResponse;
import uk.gov.justice.laa.dstew.access.query.synchronousapplication.SynchronousApplicationReadModel;

/** Maps the SynchronousApplication current-state projection to the public application response. */
@Component
public class GetSynchronousApplicationResponseMapper {

  /** Builds a response from the SynchronousApplicationReadModel. */
  public ApplicationResponse toResponse(SynchronousApplicationReadModel application) {
    ApplicationContent content = application.getApplicationContent();
    ApplicationResponse response = new ApplicationResponse();
    response.setApplicationId(application.getApplyApplicationId());
    response.setStatus(ApplicationStatus.valueOf(application.getStatus()));
    response.setLaaReference(application.getLaaReference());
    response.setLastUpdated(application.getCreatedAt().atOffset(ZoneOffset.UTC));
    response.setSubmittedAt(
        application.getSubmittedAt() == null
            ? null
            : application.getSubmittedAt().atOffset(ZoneOffset.UTC));
    response.setIsLead(true);
    response.setUsedDelegatedFunctions(application.getUsedDelegatedFunctions());
    response.setApplicationType(ApplicationType.INITIAL);
    response.setProvider(toProvider(application, content));
    response.setOpponents(toOpponents(content));
    response.setProceedings(toProceedings(application.getProceedings(), content));
    return response;
  }

  private ProviderResponse toProvider(
      SynchronousApplicationReadModel application, ApplicationContent content) {
    String contactEmail = content == null ? null : content.getSubmitterEmail();
    if (application.getOfficeCode() == null && contactEmail == null) {
      return null;
    }
    return ProviderResponse.builder()
        .officeCode(application.getOfficeCode())
        .contactEmail(contactEmail)
        .build();
  }

  private List<OpponentResponse> toOpponents(ApplicationContent content) {
    if (content == null || content.getApplicationMerits() == null) {
      return Collections.emptyList();
    }
    List<OpponentDetails> opponents = content.getApplicationMerits().getOpponents();
    if (opponents == null) {
      return Collections.emptyList();
    }
    return opponents.stream().map(this::toOpponent).toList();
  }

  private OpponentResponse toOpponent(OpponentDetails opponent) {
    Opposable opposable = opponent.getOpposable();
    return OpponentResponse.builder()
        .opponentType(opponent.getOpposableType())
        .firstName(opposable == null ? null : opposable.getFirstName())
        .lastName(opposable == null ? null : opposable.getLastName())
        .organisationName(opposable == null ? null : opposable.getName())
        .build();
  }

  private List<ApplicationProceedingResponse> toProceedings(
      List<SynchronousApplicationProceeding> proceedings, ApplicationContent content) {
    if (proceedings == null) {
      return Collections.emptyList();
    }
    return proceedings.stream().map(proceeding -> toProceeding(proceeding, content)).toList();
  }

  private ApplicationProceedingResponse toProceeding(
      SynchronousApplicationProceeding applicationProceeding, ApplicationContent content) {
    Proceeding proceeding = applicationProceeding.proceeding();
    UUID applyProceedingId =
        applicationProceeding.id() == null ? null : UUID.fromString(applicationProceeding.id());
    return ApplicationProceedingResponse.builder()
        .proceedingId(applicationProceeding.proceedingId())
        .proceedingDescription(applicationProceeding.description())
        .proceedingType(proceeding == null ? null : proceeding.getMeaning())
        .delegatedFunctionsDate(proceeding == null ? null : proceeding.getUsedDelegatedFunctionsOn())
        .categoryOfLaw(
            proceeding == null ? null : toCategoryOfLaw(proceeding.getCategoryOfLawEnum()))
        .matterType(proceeding == null ? null : toMatterType(proceeding.getMatterTypeEnum()))
        .levelOfService(
            proceeding == null ? null : proceeding.getSubstantiveLevelOfServiceNameEnum())
        .substantiveCostLimitation(
            proceeding == null ? null : proceeding.getSubstantiveCostLimitation())
        .scopeLimitations(
            proceeding == null
                ? Collections.emptyList()
                : toScopeLimitations(proceeding.getScopeLimitations()))
        .involvedChildren(toInvolvedChildren(applyProceedingId, content))
        .build();
  }

  private CategoryOfLaw toCategoryOfLaw(String categoryOfLaw) {
    return categoryOfLaw == null ? null : CategoryOfLaw.valueOf(categoryOfLaw);
  }

  private MatterType toMatterType(String matterType) {
    return matterType == null ? null : MatterType.valueOf(matterType);
  }

  private List<ScopeLimitationResponse> toScopeLimitations(
      List<Map<String, Object>> scopeLimitations) {
    if (scopeLimitations == null) {
      return Collections.emptyList();
    }
    return scopeLimitations.stream()
        .map(
            scopeLimitation ->
                ScopeLimitationResponse.builder()
                    .scopeLimitation(stringValue(scopeLimitation.get("meaning")))
                    .scopeDescription(stringValue(scopeLimitation.get("description")))
                    .build())
        .toList();
  }

  private List<InvolvedChildResponse> toInvolvedChildren(
      UUID applyProceedingId, ApplicationContent content) {
    if (applyProceedingId == null || content == null || content.getApplicationMerits() == null) {
      return Collections.emptyList();
    }
    List<ProceedingMerits> proceedingMerits = content.getProceedingMerits();
    List<InvolvedChild> involvedChildren = content.getApplicationMerits().getInvolvedChildren();
    if (proceedingMerits == null || involvedChildren == null) {
      return Collections.emptyList();
    }
    return proceedingMerits.stream()
        .filter(merits -> applyProceedingId.equals(merits.getProceedingId()))
        .flatMap(merits -> nonNullList(merits.getProceedingLinkedChildren()).stream())
        .map(ProceedingLinkedChild::getInvolvedChildId)
        .flatMap(
            childId -> involvedChildren.stream().filter(child -> childId.equals(child.getId())))
        .map(
            child ->
                new InvolvedChildResponse()
                    .fullName(child.getFullName())
                    .dateOfBirth(child.getDateOfBirth()))
        .toList();
  }

  private <T> List<T> nonNullList(List<T> values) {
    return values == null ? Collections.emptyList() : values;
  }

  private String stringValue(Object value) {
    return value == null ? null : value.toString();
  }
}


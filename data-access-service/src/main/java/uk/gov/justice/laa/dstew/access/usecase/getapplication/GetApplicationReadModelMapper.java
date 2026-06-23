package uk.gov.justice.laa.dstew.access.usecase.getapplication;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.dto.ApplicationDbProjection;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.dto.ProceedingDbProjection;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.model.ApplicationProceedingReadModel;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.model.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.model.InvolvedChildReadModel;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.model.OpponentReadModel;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.model.ProviderReadModel;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.model.ScopeLimitationReadModel;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.InvolvedChild;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.OpponentDetails;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.Opposable;

/** Composes an {@link ApplicationDbProjection} into an {@link ApplicationReadModel}. */
public class GetApplicationReadModelMapper {

  private static final String APPLICATION_TYPE_INITIAL = "INITIAL";

  /**
   * Builds the use-case read model from a raw DB projection.
   *
   * @param projection application DB projection
   * @return application read model
   */
  public ApplicationReadModel toApplicationReadModel(ApplicationDbProjection projection) {
    return ApplicationReadModel.builder()
        .id(projection.id())
        .status(projection.status())
        .laaReference(projection.laaReference())
        .updatedAt(projection.updatedAt())
        .caseworkerId(projection.caseworkerId())
        .submittedAt(projection.submittedAt())
        .isLead(projection.isLead())
        .usedDelegatedFunctions(projection.usedDelegatedFunctions())
        .autoGrant(projection.autoGrant())
        .decisionStatus(projection.decisionStatus())
        .applicationType(APPLICATION_TYPE_INITIAL)
        .version(projection.version())
        .opponents(toOpponentReadModels(projection.opponents()))
        .provider(toProviderReadModel(projection.officeCode(), projection.submitterEmail()))
        .proceedings(toProceedingReadModels(projection.proceedings()))
        .build();
  }

  private List<ApplicationProceedingReadModel> toProceedingReadModels(
      List<ProceedingDbProjection> proceedings) {
    if (proceedings == null) {
      return Collections.emptyList();
    }

    return proceedings.stream().map(this::toProceedingReadModel).toList();
  }

  private ApplicationProceedingReadModel toProceedingReadModel(ProceedingDbProjection proceeding) {
    return ApplicationProceedingReadModel.builder()
        .proceedingId(proceeding.proceedingId())
        .description(proceeding.description())
        .proceedingType(proceeding.proceedingType())
        .categoryOfLaw(proceeding.categoryOfLaw())
        .matterType(proceeding.matterType())
        .levelOfService(proceeding.levelOfService())
        .substantiveCostLimitation(proceeding.substantiveCostLimitation())
        .delegatedFunctionsDate(proceeding.delegatedFunctionsDate())
        .meritsDecision(proceeding.meritsDecision())
        .involvedChildren(toInvolvedChildReadModels(proceeding.involvedChildren()))
        .scopeLimitations(toScopeLimitationReadModels(proceeding.scopeLimitations()))
        .build();
  }

  private List<InvolvedChildReadModel> toInvolvedChildReadModels(
      List<InvolvedChild> involvedChildren) {
    if (involvedChildren == null) {
      return Collections.emptyList();
    }

    return involvedChildren.stream()
        .map(
            child ->
                InvolvedChildReadModel.builder()
                    .fullName(child.getFullName())
                    .dateOfBirth(child.getDateOfBirth())
                    .build())
        .toList();
  }

  private List<ScopeLimitationReadModel> toScopeLimitationReadModels(
      List<Map<String, Object>> scopeLimitations) {
    if (scopeLimitations == null) {
      return Collections.emptyList();
    }

    return scopeLimitations.stream()
        .map(
            scopeLimitation ->
                ScopeLimitationReadModel.builder()
                    .scopeLimitation(
                        scopeLimitation.get("meaning") != null
                            ? scopeLimitation.get("meaning").toString()
                            : null)
                    .scopeDescription(
                        scopeLimitation.get("description") != null
                            ? scopeLimitation.get("description").toString()
                            : null)
                    .build())
        .toList();
  }

  private List<OpponentReadModel> toOpponentReadModels(List<OpponentDetails> opponents) {
    if (opponents == null) {
      return Collections.emptyList();
    }

    return opponents.stream()
        .map(
            opponentDetails -> {
              Opposable opposable = opponentDetails.getOpposable();
              return OpponentReadModel.builder()
                  .opponentType(opponentDetails.getOpposableType())
                  .firstName(opposable != null ? opposable.getFirstName() : null)
                  .lastName(opposable != null ? opposable.getLastName() : null)
                  .organisationName(opposable != null ? opposable.getName() : null)
                  .build();
            })
        .toList();
  }

  private ProviderReadModel toProviderReadModel(String officeCode, String submitterEmail) {
    if (officeCode == null && submitterEmail == null) {
      return null;
    }

    return ProviderReadModel.builder().officeCode(officeCode).contactEmail(submitterEmail).build();
  }
}

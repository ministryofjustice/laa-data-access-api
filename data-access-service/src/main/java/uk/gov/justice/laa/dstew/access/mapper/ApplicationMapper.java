package uk.gov.justice.laa.dstew.access.mapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.factory.Mappers;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.MeritsDecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationProceedingResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationType;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.model.InvolvedChild;
import uk.gov.justice.laa.dstew.access.model.OpponentResponse;
import uk.gov.justice.laa.dstew.access.model.Opposable;
import uk.gov.justice.laa.dstew.access.model.ProviderResponse;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.ApplicationContent;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.ApplicationMerits;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.ProceedingMerits;

/**
 * Mapper interface. All mapping operations are performed safely, throwing an {@link
 * IllegalArgumentException} if JSON conversion fails.
 */
@Mapper(
    componentModel = "spring",
    uses = {IndividualMapper.class, ProceedingMapper.class})
public interface ApplicationMapper {

  IndividualMapper individualMapper = Mappers.getMapper(IndividualMapper.class);
  ProceedingMapper proceedingMapper = Mappers.getMapper(ProceedingMapper.class);

  /**
   * Updates an existing {@link ApplicationEntity} using values from an {@link
   * ApplicationUpdateRequest}.
   *
   * @param entity the entity to update
   * @param req the update request containing new values
   * @throws IllegalArgumentException if the {@code applicationContent} cannot be serialized
   */
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  default void updateApplicationEntity(
      @MappingTarget ApplicationEntity entity, ApplicationUpdateRequest req) {
    if (req.getStatus() != null) {
      entity.setStatus(req.getStatus());
    }
    entity.setApplicationContent(req.getApplicationContent());
  }

  /**
   * Maps a {@link ApplicationEntity} to an API-facing {@link ApplicationResponse} model.
   *
   * @param entity the entity to map
   * @return a new {@link ApplicationResponse} sobject, or {@code null} if the entity is null
   * @throws IllegalArgumentException if the {@code applicationContent} cannot be deserialized
   */
  default ApplicationResponse toApplication(ApplicationEntity entity) {
    if (entity == null) {
      return null;
    }

    ApplicationResponse application = new ApplicationResponse();
    application.setApplicationId(entity.getId());
    application.setStatus(entity.getStatus());
    application.setLaaReference(entity.getLaaReference());
    application.setLastUpdated(OffsetDateTime.ofInstant(entity.getUpdatedAt(), ZoneOffset.UTC));
    application.assignedTo(entity.getCaseworker() != null ? entity.getCaseworker().getId() : null);
    application.setLastUpdated(OffsetDateTime.ofInstant(entity.getUpdatedAt(), ZoneOffset.UTC));
    application.setSubmittedAt(
        entity.getSubmittedAt() != null
            ? OffsetDateTime.ofInstant(entity.getSubmittedAt(), ZoneOffset.UTC)
            : null);
    application.setIsLead(entity.isLead());
    application.setUsedDelegatedFunctions(entity.getUsedDelegatedFunctions());
    application.setAutoGrant(entity.getIsAutoGranted());
    if (entity.getDecision() != null) {
      application.setDecisionStatus(entity.getDecision().getOverallDecision());
    }
    application.setApplicationType(ApplicationType.INITIAL);

    ApplicationContent applicationContent =
        extractApplicationContent(entity.getApplicationContent());

    application.setOpponents(extractOpponents(applicationContent));
    application.setProvider(extractProvider(entity, applicationContent));
    application.setVersion(entity.getVersion());

    if (applicationContent != null && entity.getProceedings() != null) {
      List<ProceedingMerits> proceedingMeritsList = applicationContent.getProceedingMerits();
      List<InvolvedChild> involvedChildren = extractInvolvedChildren(applicationContent);

      application.setProceedings(
          entity.getProceedings().stream()
              .map(p -> toApplicationProceeding(p, proceedingMeritsList, involvedChildren))
              .toList());
    }

    return application;
  }

  private static ApplicationProceedingResponse toApplicationProceeding(
      ProceedingEntity proceeding,
      List<ProceedingMerits> proceedingMeritsList,
      List<InvolvedChild> involvedChildren) {

    if (proceeding == null) {
      return null;
    }

    ApplicationProceedingResponse proceedingResponse =
        proceedingMapper.toApplicationProceeding(
            proceeding, proceedingMeritsList, involvedChildren);

    MeritsDecisionEntity meritsDecision = proceeding.getMeritsDecision();
    proceedingResponse.setMeritsDecision(
        meritsDecision != null ? meritsDecision.getDecision() : null);

    return proceedingResponse;
  }

  private static ProviderResponse extractProvider(
      ApplicationEntity entity, ApplicationContent applicationContent) {
    String officeCode = entity.getOfficeCode();
    String contactEmail =
        applicationContent != null ? applicationContent.getSubmitterEmail() : null;

    if (officeCode == null && contactEmail == null) {
      return null;
    }

    ProviderResponse providerResponse = new ProviderResponse();
    providerResponse.setOfficeCode(officeCode);
    providerResponse.setContactEmail(contactEmail);
    return providerResponse;
  }

  private static ApplicationContent extractApplicationContent(Map<String, Object> content) {
    if (content == null) {
      return null;
    }
    return MapperUtil.getObjectMapper().convertValue(content, ApplicationContent.class);
  }

  private static List<InvolvedChild> extractInvolvedChildren(
      ApplicationContent applicationContent) {

    ApplicationMerits meritsObj = applicationContent.getApplicationMerits();
    if (meritsObj == null) {
      return Collections.emptyList();
    }
    List<InvolvedChild> children = meritsObj.getInvolvedChildren();
    return children != null ? children : Collections.emptyList();
  }

  private static List<OpponentResponse> extractOpponents(ApplicationContent applicationContent) {
    if (applicationContent == null || applicationContent.getApplicationMerits() == null) {
      return Collections.emptyList();
    }

    ApplicationMerits merits = applicationContent.getApplicationMerits();
    if (merits.getOpponents() == null) {
      return Collections.emptyList();
    }

    return merits.getOpponents().stream()
        .map(
            opponentDetails -> {
              Opposable opposable = opponentDetails.getOpposable();
              return OpponentResponse.builder()
                  .opponentType(opponentDetails.getOpposableType())
                  .firstName(opposable != null ? opposable.getFirstName() : null)
                  .lastName(opposable != null ? opposable.getLastName() : null)
                  .organisationName(opposable != null ? opposable.getName() : null)
                  .build();
            })
        .toList();
  }
}

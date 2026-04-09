package uk.gov.justice.laa.dstew.access.mapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.factory.Mappers;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationMerits;
import uk.gov.justice.laa.dstew.access.model.ApplicationResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationType;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.model.OpponentDetails;
import uk.gov.justice.laa.dstew.access.model.OpponentResponse;
import uk.gov.justice.laa.dstew.access.model.ProviderResponse;

/**
 * Mapper interface. All mapping operations are performed safely, throwing an {@link
 * IllegalArgumentException} if JSON conversion fails.
 */
@Mapper(
    componentModel = "spring",
    uses = {IndividualMapper.class})
public interface ApplicationMapper {

  IndividualMapper individualMapper = Mappers.getMapper(IndividualMapper.class);

  /**
   * Converts a {@link ApplicationCreateRequest} model into a new {@link ApplicationEntity}.
   *
   * @param req the CREATE request to map
   * @return a new {@link ApplicationEntity} populated from the request, or {@code null} if the
   *     request is null
   * @throws IllegalArgumentException if the {@code applicationContent} cannot be serialized
   */
  default ApplicationEntity toApplicationEntity(ApplicationCreateRequest req) {
    if (req == null) {
      return null;
    }
    ApplicationEntity entity = new ApplicationEntity();
    entity.setStatus(req.getStatus());
    entity.setLaaReference(req.getLaaReference());
    ObjectMapper mapper = MapperUtil.getObjectMapper();
    var individuals =
        req.getIndividuals().stream()
            .map(individualMapper::toIndividualEntity)
            .collect(Collectors.toSet());
    entity.setApplicationContent(mapper.convertValue(req.getApplicationContent(), Map.class));
    entity.setIndividuals(individuals);
    return entity;
  }

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
      application.setOverallDecision(entity.getDecision().getOverallDecision());
    }
    application.setApplicationType(ApplicationType.INITIAL);
    application.setOpponents(extractOpponents(entity.getApplicationContent()));
    application.setProvider(extractProvider(entity));
    application.setVersion(entity.getVersion());

    return application;
  }

  private static ProviderResponse extractProvider(ApplicationEntity entity) {
    String officeCode = entity.getOfficeCode();
    String contactEmail = extractContactEmail(entity.getApplicationContent());

    if (officeCode == null && contactEmail == null) {
      return null;
    }

    ProviderResponse providerResponse = new ProviderResponse();
    providerResponse.setOfficeCode(officeCode);
    providerResponse.setContactEmail(contactEmail);
    return providerResponse;
  }

  private static String extractContactEmail(Map<String, Object> content) {
    if (content == null) {
      return null;
    }

    ApplicationContent applicationContent =
        MapperUtil.getObjectMapper().convertValue(content, ApplicationContent.class);

    return applicationContent.getSubmitterEmail();
  }

  private static List<OpponentResponse> extractOpponents(Map<String, Object> content) {

    if (content == null) {
      return Collections.emptyList();
    }

    ApplicationContent applicationContent =
        MapperUtil.getObjectMapper().convertValue(content, ApplicationContent.class);
    ApplicationMerits meritsObj = applicationContent.getApplicationMerits();
    if (meritsObj == null) {
      return Collections.emptyList();
    }

    List<OpponentDetails> opponentsList = meritsObj.getOpponents();
    if (opponentsList == null) {
      return Collections.emptyList();
    }

    return opponentsList.stream()
        .map(OpponentDetails::getOpposable)
        .map(
            opposableObj ->
                OpponentResponse.builder()
                    .opposableType(opposableObj.getOpposableType())
                    .firstName(opposableObj.getFirstName())
                    .lastName(opposableObj.getLastName())
                    .organisationName(opposableObj.getName())
                    .build())
        .toList();
  }
}

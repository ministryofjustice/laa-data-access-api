package uk.gov.justice.laa.dstew.access.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.factory.Mappers;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.model.ProceedingDetail;


/**
 * Mapper interface.
 * All mapping operations are performed safely, throwing an
 * {@link IllegalArgumentException} if JSON conversion fails.
 */
@Mapper(componentModel = "spring", uses = {IndividualMapper.class})
public interface ApplicationMapper {

  IndividualMapper individualMapper = Mappers.getMapper(IndividualMapper.class);

  /**
   * Converts a {@link ApplicationCreateRequest} model into a new {@link ApplicationEntity}.
   *
   * @param req the CREATE request to map
   * @return a new {@link ApplicationEntity} populated from the request, or {@code null} if the request is null
   * @throws IllegalArgumentException if the {@code applicationContent} cannot be serialized
   */
  default ApplicationEntity toApplicationEntity(ApplicationCreateRequest req, ObjectMapper mapper) {
    if (req == null) {
      return null;
    }

    ApplicationEntity entity = new ApplicationEntity();
    ApplicationContent applicationContent = mapper.convertValue(req.getApplicationContent(), ApplicationContent.class);
    processingApplicationContent(entity, applicationContent);
    entity.setStatus(req.getStatus());
    entity.setLaaReference(req.getLaaReference());
    var individuals = req.getIndividuals()
        .stream()
        .map(individualMapper::toIndividualEntity)
        .collect(Collectors.toSet());
    entity.setIndividuals(individuals);
    entity.setApplicationContent(req.getApplicationContent());
    return entity;
  }

  /**
   * Processes application content to extract and set key fields in the entity.
   *
   * @param entity             the application entity to update
   * @param applicationContent the application content to process
   */
  private void processingApplicationContent(ApplicationEntity entity, ApplicationContent applicationContent) {
    if (applicationContent == null) {
      return;
    }
    if (applicationContent.getProceedings() == null || applicationContent.getProceedings().isEmpty()) {
      return;
    }
    ProceedingDetail leadProceeding = applicationContent.getProceedings().stream()
        .filter(Objects::nonNull)
        .filter(ProceedingDetail::leadProceeding)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No lead proceeding found in application content"));
    boolean usedDelegatedFunction =
        applicationContent.getProceedings().stream().filter(Objects::nonNull)
            .anyMatch(ProceedingDetail::useDelegatedFunctions);
    entity.setAutoGranted(applicationContent.isAutoGrant());
    entity.setUseDelegatedFunctions(usedDelegatedFunction);
    entity.setCategoryOfLaw(leadProceeding.categoryOfLaw());
    entity.setMatterType(leadProceeding.matterType());
  }

  /**
   * Updates an existing {@link ApplicationEntity} using values from an {@link ApplicationUpdateRequest}.
   *
   * @param entity the entity to update
   * @param req    the update request containing new values
   * @throws IllegalArgumentException if the {@code applicationContent} cannot be serialized
   */
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  default void updateApplicationEntity(@MappingTarget ApplicationEntity entity, ApplicationUpdateRequest req) {
    if (req.getStatus() != null) {
      entity.setStatus(req.getStatus());
    }
    if (req.getApplicationContent() != null) {
      entity.setApplicationContent(req.getApplicationContent());
    }
  }


  /**
   * Maps a {@link ApplicationEntity} to an API-facing {@link Application} model.
   *
   * @param entity the entity to map
   * @return a new {@link Application} object, or {@code null} if the entity is null
   * @throws IllegalArgumentException if the {@code applicationContent} cannot be deserialized
   */
  default Application toApplication(ApplicationEntity entity) {
    if (entity == null) {
      return null;
    }

    Application application = new Application();
    application.setId(entity.getId());
    application.setApplicationStatus(entity.getStatus());
    application.setSchemaVersion(entity.getSchemaVersion());
    application.setApplicationContent(entity.getApplicationContent());
    application.setLaaReference(entity.getLaaReference());
    application.caseworkerId(entity.getCaseworker() != null ? entity.getCaseworker().getId() : null);
    application.setCreatedAt(OffsetDateTime.ofInstant(entity.getCreatedAt(), ZoneOffset.UTC));
    application.setUpdatedAt(OffsetDateTime.ofInstant(entity.getUpdatedAt(), ZoneOffset.UTC));

    application.setIndividuals(
        Optional.ofNullable(entity.getIndividuals())
            .orElse(Set.of())
            .stream()
            .map(individualMapper::toIndividual)
            .filter(Objects::nonNull)
            .toList()
    );

    return application;
  }


}

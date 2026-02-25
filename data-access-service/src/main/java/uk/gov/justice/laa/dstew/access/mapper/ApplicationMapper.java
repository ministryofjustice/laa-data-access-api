package uk.gov.justice.laa.dstew.access.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.factory.Mappers;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationType;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.model.Individual;
import uk.gov.justice.laa.dstew.access.model.Opponent;

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
  default ApplicationEntity toApplicationEntity(ApplicationCreateRequest req) {
    if (req == null) {
      return null;
    }
    ApplicationEntity entity = new ApplicationEntity();
    entity.setStatus(req.getStatus());
    entity.setLaaReference(req.getLaaReference());
    ObjectMapper mapper = MapperUtil.getObjectMapper();
    var individuals = req.getIndividuals()
        .stream()
        .map(individualMapper::toIndividualEntity)
        .collect(Collectors.toSet());
    entity.setApplicationContent(mapper.convertValue(req.getApplicationContent(), Map.class));
    entity.setIndividuals(individuals);
    return entity;
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
    entity.setApplicationContent(req.getApplicationContent());
  }

  /**
   * Maps a {@link ApplicationEntity} to an API-facing {@link Application} model.
   *
   * @param entity the entity to map
   * @return a new {@link Application} sobject, or {@code null} if the entity is null
   * @throws IllegalArgumentException if the {@code applicationContent} cannot be deserialized
   */
  default Application toApplication(ApplicationEntity entity) {
    if (entity == null) {
      return null;
    }

    Application application = new Application();
    application.setApplicationId(entity.getId());
    application.setStatus(entity.getStatus());
    application.setLaaReference(entity.getLaaReference());
    application.setLastUpdated(OffsetDateTime.ofInstant(entity.getUpdatedAt(), ZoneOffset.UTC));
    application.assignedTo(entity.getCaseworker() != null ? entity.getCaseworker().getId() : null);
    application.setLastUpdated(OffsetDateTime.ofInstant(entity.getUpdatedAt(), ZoneOffset.UTC));
    application.setSubmittedAt(
        entity.getSubmittedAt() != null
            ? OffsetDateTime.ofInstant(entity.getSubmittedAt(), ZoneOffset.UTC)
            : null
    );
    application.setIsLead(entity.isLead());
    application.setUseDelegatedFunctions(entity.getUsedDelegatedFunctions());
    application.setAutoGrant(entity.getIsAutoGranted());
    if (entity.getDecision() != null) {
      application.setOverallDecision(entity.getDecision().getOverallDecision());
    }
    application.setApplicationType(ApplicationType.INITIAL);
    application.setOpponents(
        extractOpponents(entity.getApplicationContent())
    );
    application.setProvider(entity.getOfficeCode());

    return application;
  }

  private static List<Individual> getIndividuals(Set<IndividualEntity> individuals) {
    return individuals
        .stream()
        .map(individualMapper::toIndividual)
        .filter(Objects::nonNull)
        .toList();
  }

  private static List<Opponent> extractOpponents(Map<String, Object> content) {

    if (content == null) {
      return null;
    }

    Object meritsObj = content.get("applicationMerits");
    if (!(meritsObj instanceof Map<?, ?> meritsMap)) {
      return null;
    }

    Object opponentsObj = meritsMap.get("opponents");
    if (!(opponentsObj instanceof List<?> opponentsList)) {
      return null;
    }

    return opponentsList.stream()
        .filter(Map.class::isInstance)
        .map(Map.class::cast)
        .map(opponentMap -> {

          Object opposableObj = opponentMap.get("opposable");
          if (!(opposableObj instanceof Map<?, ?> opposable)) {
            return null;
          }

          String opposableType = (String) opposable.get("opposableType");
          String firstName = (String) opposable.get("firstName");
          String lastName = (String) opposable.get("lastName");
          String organisationName = (String) opposable.get("name");

          if (opposableType == null &&
              firstName == null &&
              lastName == null &&
              organisationName == null) {
            return null;
          }

          Opponent opponent = new Opponent();
          opponent.setOpposableType(opposableType);
          opponent.setFirstName(firstName);
          opponent.setLastName(lastName);
          opponent.setOrganisationName(organisationName);

          return opponent;
        })
        .filter(Objects::nonNull)
        .toList();
  }
}
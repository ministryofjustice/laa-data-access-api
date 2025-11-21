package uk.gov.justice.laa.dstew.access.mapper;

import org.mapstruct.Mapper;
import uk.gov.justice.laa.dstew.access.entity.LinkedIndividualEntity;
import uk.gov.justice.laa.dstew.access.model.Individual;

/**
 * Mapper interface for Linked Individuals.
 * All mapping operations are performed safely,
 * gracefully handling null values.
 */
@Mapper(componentModel = "spring")
public interface LinkedIndividualMapper {

  /**
   * Converts a {@link LinkedIndividualEntity} to an API-facing {@link Individual} model.
   * Safely handles nulls: if the {@code entity} itself or its {@code linkedIndividual} is null,
   * the method returns {@code null}.
   *
   * @param entity the {@link LinkedIndividualEntity} to map (might be null)
   * @return a new {@link Individual} object populated with first name, last name, date of birth,
   *         and individual content, or {@code null} if the input or linked individual is null
   */
  default Individual toIndividual(LinkedIndividualEntity entity) {
    if (entity == null || entity.getLinkedIndividual() == null) {
      return null;
    }

    var linked = entity.getLinkedIndividual();

    Individual dto = new Individual();
    dto.setFirstName(linked.getFirstName());
    dto.setLastName(linked.getLastName());
    dto.setDateOfBirth(linked.getDateOfBirth());
    dto.setDetails(linked.getIndividualContent());
    return dto;
  }
}

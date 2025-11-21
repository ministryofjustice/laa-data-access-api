package uk.gov.justice.laa.dstew.access.mapper;

import org.mapstruct.Mapper;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.Individual;

/**
 * Mapper interface for Individuals.
 * All mapping operations are performed safely,
 * gracefully handling null values.
 */
@Mapper(componentModel = "spring")
public interface IndividualMapper {

  /**
   * Converts a {@link IndividualEntity} to an API-facing {@link Individual} model.
   * Safely handles nulls: if the {@code entity} itself is null,
   * the method returns {@code null}.
   *
   * @param entity the {@link IndividualEntity} to map (might be null)
   * @return a new {@link Individual} object populated with first name, last name, date of birth,
   *         and individual content, or {@code null} if the input or individual is null
   */
  default Individual toIndividual(IndividualEntity entity) {
    if (entity == null) {
      return null;
    }

    Individual dto = new Individual();
    dto.setFirstName(entity.getFirstName());
    dto.setLastName(entity.getLastName());
    dto.setDateOfBirth(entity.getDateOfBirth());
    dto.setDetails(entity.getIndividualContent());
    return dto;
  }
}

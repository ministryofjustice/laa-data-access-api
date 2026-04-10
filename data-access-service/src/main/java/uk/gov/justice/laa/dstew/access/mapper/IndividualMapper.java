package uk.gov.justice.laa.dstew.access.mapper;

import org.mapstruct.Mapper;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.model.IncludedAdditionalData;
import uk.gov.justice.laa.dstew.access.model.IndividualCreateRequest;
import uk.gov.justice.laa.dstew.access.model.IndividualResponse;
import uk.gov.justice.laa.dstew.access.model.IndividualType;

/**
 * Mapper interface for Individuals. All mapping operations are performed safely, gracefully
 * handling null values.
 */
@Mapper(componentModel = "spring")
public interface IndividualMapper {

  /**
   * Converts a {@link IndividualEntity} to an API-facing {@link IndividualResponse} model. Safely
   * handles nulls: if the {@code entity} itself is null, the method returns {@code null}.
   *
   * @param entity the {@link IndividualEntity} to map (might be null)
   * @return a new {@link IndividualResponse} object populated with first name, last name, date of
   *     birth, and individual content, or {@code null} if the input or individual is null
   */
  default IndividualResponse toIndividual(IndividualEntity entity) {
    if (entity == null) {
      return null;
    }

    IndividualResponse dto = new IndividualResponse();
    dto.setFirstName(entity.getFirstName());
    dto.setLastName(entity.getLastName());
    dto.setDateOfBirth(entity.getDateOfBirth());
    dto.setDetails(entity.getIndividualContent());
    dto.setType(entity.getType());
    return dto;
  }

  /**
   * Converts a {@link IndividualEntity} to an API-facing {@link IndividualResponse} model with
   * extended client details from the application content. Safely handles nulls: if the {@code
   * entity} itself is null, the method returns {@code null}.
   *
   * @param entity the {@link IndividualEntity} to map (might be null)
   * @param individualType the type of individual
   * @param include the additional data to include
   * @param applicationContent the application content containing client details
   * @return a new {@link IndividualResponse} object populated with first name, last name, date of
   *     birth, and individual content, or {@code null} if the input or individual is null
   */
  default IndividualResponse toExtendedIndividual(
      IndividualEntity entity,
      IndividualType individualType,
      IncludedAdditionalData include,
      ApplicationContent applicationContent) {

    IndividualResponse dto = toIndividual(entity);

    if (dto == null) {
      return null;
    }

    dto.setClientId(null);
    dto.setLastNameAtBirth(null);
    dto.setPreviousApplicationId(null);
    dto.setRelationshipToChildren(null);
    dto.setCorrespondenceAddressType(null);
    dto.setAppliedPreviously(null);
    dto.setCorrespondenceAddress(null);

    // only populate fields if rules are set
    if (individualType == IndividualType.CLIENT
        && include == IncludedAdditionalData.CLIENT_DETAILS) {
      dto.setClientId(entity.getId());
      dto.setLastNameAtBirth(applicationContent.getLastNameAtBirth());
      dto.setPreviousApplicationId(applicationContent.getPreviousApplicationId());
      dto.setRelationshipToChildren(applicationContent.getRelationshipToChildren());
      dto.setCorrespondenceAddressType(applicationContent.getCorrespondenceAddressType());
      if (applicationContent.getApplicant() != null) {
        dto.setAppliedPreviously(applicationContent.getApplicant().getAppliedPreviously());
        dto.setCorrespondenceAddress(applicationContent.getApplicant().getAddresses());
      }
    }
    return dto;
  }

  /**
   * Converts API model {@link IndividualResponse} to an database entity {@link IndividualEntity}
   * model. Safely handles nulls: if the {@code individual} itself is null, the method returns
   * {@code null}.
   *
   * @param individualResponse API model the {@link IndividualResponse} to map (might be null)
   * @return a new {@link IndividualEntity} object populated with first name, last name, date of
   *     birth, and individual content, or {@code null} if the input or individual is null
   */
  default IndividualEntity toIndividualEntity(IndividualResponse individualResponse) {
    return individualResponse == null
        ? null
        : IndividualEntity.builder()
            .firstName(individualResponse.getFirstName())
            .lastName(individualResponse.getLastName())
            .dateOfBirth(individualResponse.getDateOfBirth())
            .individualContent(individualResponse.getDetails())
            .type(individualResponse.getType())
            .build();
  }

  /**
   * Converts API model {@link IndividualCreateRequest} to an database entity {@link
   * IndividualEntity} model. Safely handles nulls: if the {@code individual} itself is null, the
   * method returns {@code null}.
   *
   * @param individual API model the {@link IndividualCreateRequest} to map (might be null)
   * @return a new {@link IndividualEntity} object populated with first name, last name, date of
   *     birth, and individual content, or {@code null} if the input or individual is null
   */
  default IndividualEntity toIndividualEntity(IndividualCreateRequest individual) {
    return individual == null
        ? null
        : IndividualEntity.builder()
            .firstName(individual.getFirstName())
            .lastName(individual.getLastName())
            .dateOfBirth(individual.getDateOfBirth())
            .individualContent(individual.getDetails())
            .type(individual.getType())
            .build();
  }
}

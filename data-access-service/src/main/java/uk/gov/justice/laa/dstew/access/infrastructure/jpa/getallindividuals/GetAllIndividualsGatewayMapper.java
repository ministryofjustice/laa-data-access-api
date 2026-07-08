package uk.gov.justice.laa.dstew.access.infrastructure.jpa.getallindividuals;

import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.domain.ApplicationClientDetailsDomain;
import uk.gov.justice.laa.dstew.access.domain.IndividualDomain;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.ApplicationApplicant;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.ApplicationContent;

/** Maps JPA entities to domain types for the get-all-individuals use case. */
public class GetAllIndividualsGatewayMapper {

  private final ObjectMapper objectMapper;

  /**
   * Constructs the mapper.
   *
   * @param objectMapper Jackson object mapper
   */
  public GetAllIndividualsGatewayMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Maps an individual entity to an {@link IndividualDomain}.
   *
   * @param individualEntity the entity to map
   * @return domain record
   */
  public IndividualDomain toDomain(IndividualEntity individualEntity) {
    return IndividualDomain.builder()
        .id(individualEntity.getId())
        .firstName(individualEntity.getFirstName())
        .lastName(individualEntity.getLastName())
        .dateOfBirth(individualEntity.getDateOfBirth())
        .individualContent(individualEntity.getIndividualContent())
        .type(individualEntity.getType() != null ? individualEntity.getType().name() : null)
        .build();
  }

  /**
   * Extracts CLIENT_DETAILS fields from an application entity by deserialising applicationContent.
   *
   * @param applicationEntity the application entity
   * @return client details domain record
   */
  public ApplicationClientDetailsDomain toClientDetails(ApplicationEntity applicationEntity) {
    ApplicationContent content =
        objectMapper.convertValue(
            applicationEntity.getApplicationContent(), ApplicationContent.class);

    ApplicationClientDetailsDomain.ApplicationClientDetailsDomainBuilder builder =
        ApplicationClientDetailsDomain.builder();

    if (content == null) {
      return builder.build();
    }

    builder
        .lastNameAtBirth(content.getLastNameAtBirth())
        .previousApplicationId(content.getPreviousApplicationId())
        .correspondenceAddressType(content.getCorrespondenceAddressType());

    ApplicationApplicant applicant = content.getApplicant();
    if (applicant == null) {
      return builder.build();
    }

    return builder
        .relationshipToInvolvedChildren(applicant.getRelationshipToInvolvedChildren())
        .appliedPreviously(applicant.getAppliedPreviously())
        .correspondenceAddress(applicant.getAddresses())
        .build();
  }
}

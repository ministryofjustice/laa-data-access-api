package uk.gov.justice.laa.dstew.access.infrastructure.jpa.getallindividuals;

import java.util.List;
import java.util.Map;
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
    ApplicationApplicant applicant = content != null ? content.getApplicant() : null;
    List<Map<String, Object>> addresses = applicant != null ? applicant.getAddresses() : null;
    String relationship = applicant != null ? applicant.getRelationshipToInvolvedChildren() : null;
    Boolean appliedPreviously = applicant != null ? applicant.getAppliedPreviously() : null;

    return ApplicationClientDetailsDomain.builder()
        .lastNameAtBirth(content != null ? content.getLastNameAtBirth() : null)
        .previousApplicationId(content != null ? content.getPreviousApplicationId() : null)
        .relationshipToInvolvedChildren(relationship)
        .correspondenceAddressType(content != null ? content.getCorrespondenceAddressType() : null)
        .appliedPreviously(appliedPreviously)
        .correspondenceAddress(addresses)
        .build();
  }
}

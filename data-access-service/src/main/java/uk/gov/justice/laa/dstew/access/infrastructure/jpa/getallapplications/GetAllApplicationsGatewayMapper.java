package uk.gov.justice.laa.dstew.access.infrastructure.jpa.getallapplications;

import java.util.List;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryDto;
import uk.gov.justice.laa.dstew.access.model.IndividualSummaryDto;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummaryDto;
import uk.gov.justice.laa.dstew.access.usecase.getallapplications.model.ApplicationSummaryReadModel;
import uk.gov.justice.laa.dstew.access.usecase.getallapplications.model.LinkedApplicationSummaryReadModel;

/** Maps JPA DTOs to read models for the getAllApplications use case. */
public class GetAllApplicationsGatewayMapper {

  private static final String APPLICATION_TYPE_INITIAL = "INITIAL";

  /**
   * Maps a {@link ApplicationSummaryDto} to an {@link ApplicationSummaryReadModel}.
   *
   * @param applicationSummaryDto the source DTO
   * @return the read model, or {@code null} if the input is {@code null}
   */
  public ApplicationSummaryReadModel toApplicationSummaryReadModel(
      ApplicationSummaryDto applicationSummaryDto) {
    if (applicationSummaryDto == null) {
      return null;
    }
    List<IndividualSummaryDto> individuals = applicationSummaryDto.getIndividuals();
    IndividualSummaryDto client =
        individuals == null
            ? null
            : individuals.stream()
                .filter(i -> IndividualType.CLIENT.equals(i.getType()))
                .findFirst()
                .orElse(null);

    ApplicationSummaryReadModel.ApplicationSummaryReadModelBuilder builder =
        ApplicationSummaryReadModel.builder()
            .id(applicationSummaryDto.getId())
            .submittedAt(applicationSummaryDto.getSubmittedAt())
            .isAutoGranted(applicationSummaryDto.getIsAutoGranted())
            .categoryOfLaw(
                applicationSummaryDto.getCategoryOfLaw() != null
                    ? applicationSummaryDto.getCategoryOfLaw().name()
                    : null)
            .matterType(
                applicationSummaryDto.getMatterType() != null
                    ? applicationSummaryDto.getMatterType().name()
                    : null)
            .usedDelegatedFunctions(applicationSummaryDto.getUsedDelegatedFunctions())
            .laaReference(applicationSummaryDto.getLaaReference())
            .officeCode(applicationSummaryDto.getOfficeCode())
            .status(
                applicationSummaryDto.getStatus() != null
                    ? applicationSummaryDto.getStatus().name()
                    : null)
            .caseworkerId(applicationSummaryDto.getCaseworkerId())
            .applicationType(APPLICATION_TYPE_INITIAL)
            .modifiedAt(applicationSummaryDto.getModifiedAt())
            .isLead(applicationSummaryDto.isLead())
            .linkedApplications(List.of());

    if (client == null) {
      return builder.build();
    }

    return builder
        .clientFirstName(client.getFirstName())
        .clientLastName(client.getLastName())
        .clientDateOfBirth(client.getDateOfBirth())
        .build();
  }

  /**
   * Maps a {@link LinkedApplicationSummaryDto} to a {@link LinkedApplicationSummaryReadModel}.
   *
   * @param linkedApplicationSummaryDto the source DTO
   * @return the read model, or {@code null} if the input is {@code null}
   */
  public LinkedApplicationSummaryReadModel toLinkedApplicationSummaryReadModel(
      LinkedApplicationSummaryDto linkedApplicationSummaryDto) {
    if (linkedApplicationSummaryDto == null) {
      return null;
    }
    return LinkedApplicationSummaryReadModel.builder()
        .applicationId(linkedApplicationSummaryDto.getApplicationId())
        .laaReference(linkedApplicationSummaryDto.getLaaReference())
        .isLead(linkedApplicationSummaryDto.getIsLead())
        .leadApplicationId(linkedApplicationSummaryDto.getLeadApplicationId())
        .build();
  }
}

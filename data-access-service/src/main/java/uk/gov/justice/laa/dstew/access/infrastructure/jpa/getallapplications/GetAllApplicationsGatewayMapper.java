package uk.gov.justice.laa.dstew.access.infrastructure.jpa.getallapplications;

import java.util.List;
import uk.gov.justice.laa.dstew.access.domain.ApplicationSummaryDomain;
import uk.gov.justice.laa.dstew.access.domain.LinkedApplicationSummaryDomain;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryDto;
import uk.gov.justice.laa.dstew.access.model.IndividualSummaryDto;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummaryDto;

/** Maps JPA DTOs to domain records for the getAllApplications use case. */
public class GetAllApplicationsGatewayMapper {

  private static final String APPLICATION_TYPE_INITIAL = "INITIAL";

  /**
   * Maps a {@link ApplicationSummaryDto} to an {@link ApplicationSummaryDomain}.
   *
   * @param applicationSummaryDto the source DTO
   * @return the domain record, or {@code null} if the input is {@code null}
   */
  public ApplicationSummaryDomain toApplicationSummaryDomain(
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

    ApplicationSummaryDomain.ApplicationSummaryDomainBuilder builder =
        ApplicationSummaryDomain.builder()
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
   * Maps a {@link LinkedApplicationSummaryDto} to a {@link LinkedApplicationSummaryDomain}.
   *
   * @param linkedApplicationSummaryDto the source DTO
   * @return the domain record, or {@code null} if the input is {@code null}
   */
  public LinkedApplicationSummaryDomain toLinkedApplicationSummaryDomain(
      LinkedApplicationSummaryDto linkedApplicationSummaryDto) {
    if (linkedApplicationSummaryDto == null) {
      return null;
    }
    return LinkedApplicationSummaryDomain.builder()
        .applicationId(linkedApplicationSummaryDto.getApplicationId())
        .laaReference(linkedApplicationSummaryDto.getLaaReference())
        .isLead(linkedApplicationSummaryDto.getIsLead())
        .leadApplicationId(linkedApplicationSummaryDto.getLeadApplicationId())
        .build();
  }
}

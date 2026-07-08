package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.dstew.access.domain.ApplicationSummaryDomain;
import uk.gov.justice.laa.dstew.access.domain.LinkedApplicationSummaryDomain;
import uk.gov.justice.laa.dstew.access.domain.PagedResultDomain;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationType;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.usecase.getallapplications.GetAllApplicationsResult;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domain.ApplicationSummaryDomainGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domain.LinkedApplicationSummaryDomainGenerator;

class GetAllApplicationsResponseMapperTest {

  private final GetAllApplicationsResponseMapper mapper = new GetAllApplicationsResponseMapper();

  @Test
  void givenFullyPopulatedResult_whenToGetAllApplicationsResponse_thenAllFieldsMapped() {
    UUID id = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    UUID linkedAppId = UUID.randomUUID();
    Instant submittedAt = Instant.parse("2024-01-01T10:00:00Z");
    Instant modifiedAt = Instant.parse("2024-06-01T12:00:00Z");
    LocalDate dob = LocalDate.of(1990, 5, 15);

    LinkedApplicationSummaryDomain linked =
        DataGenerator.createDefault(
            LinkedApplicationSummaryDomainGenerator.class,
            b -> b.applicationId(linkedAppId).laaReference("REF456").isLead(false));

    ApplicationSummaryDomain domain =
        DataGenerator.createDefault(
            ApplicationSummaryDomainGenerator.class,
            b ->
                b.id(id)
                    .submittedAt(submittedAt)
                    .isAutoGranted(true)
                    .categoryOfLaw("FAMILY")
                    .matterType("SPECIAL_CHILDREN_ACT")
                    .usedDelegatedFunctions(true)
                    .laaReference("REF123")
                    .officeCode("1A234B")
                    .status("APPLICATION_SUBMITTED")
                    .caseworkerId(caseworkerId)
                    .clientFirstName("Jane")
                    .clientLastName("Doe")
                    .clientDateOfBirth(dob)
                    .applicationType("INITIAL")
                    .modifiedAt(modifiedAt)
                    .isLead(true)
                    .linkedApplications(List.of(linked)));

    PagedResultDomain<ApplicationSummaryDomain> page = new PagedResultDomain<>(List.of(domain), 1);
    GetAllApplicationsResult result = new GetAllApplicationsResult(page, 1, 10);

    ResponseEntity<ApplicationSummaryResponse> response =
        mapper.toGetAllApplicationsResponse(result);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    ApplicationSummaryResponse body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getPaging().getPage()).isEqualTo(1);
    assertThat(body.getPaging().getPageSize()).isEqualTo(10);
    assertThat(body.getPaging().getTotalRecords()).isEqualTo(1);
    assertThat(body.getPaging().getItemsReturned()).isEqualTo(1);

    ApplicationSummary app = body.getApplications().get(0);
    assertThat(app.getApplicationId()).isEqualTo(id);
    assertThat(app.getSubmittedAt()).isEqualTo(submittedAt.atOffset(ZoneOffset.UTC));
    assertThat(app.getAutoGrant()).isTrue();
    assertThat(app.getCategoryOfLaw()).isEqualTo(CategoryOfLaw.FAMILY);
    assertThat(app.getMatterType()).isEqualTo(MatterType.SPECIAL_CHILDREN_ACT);
    assertThat(app.getUsedDelegatedFunctions()).isTrue();
    assertThat(app.getLaaReference()).isEqualTo("REF123");
    assertThat(app.getOfficeCode()).isEqualTo("1A234B");
    assertThat(app.getStatus()).isEqualTo(ApplicationStatus.APPLICATION_SUBMITTED);
    assertThat(app.getAssignedTo()).isEqualTo(caseworkerId);
    assertThat(app.getClientFirstName()).isEqualTo("Jane");
    assertThat(app.getClientLastName()).isEqualTo("Doe");
    assertThat(app.getClientDateOfBirth()).isEqualTo(dob);
    assertThat(app.getApplicationType()).isEqualTo(ApplicationType.INITIAL);
    assertThat(app.getLastUpdated())
        .isEqualTo(OffsetDateTime.ofInstant(modifiedAt, ZoneOffset.UTC));
    assertThat(app.getIsLead()).isTrue();
    assertThat(app.getLinkedApplications()).hasSize(1);
    assertThat(app.getLinkedApplications().get(0).getApplicationId()).isEqualTo(linkedAppId);
    assertThat(app.getLinkedApplications().get(0).getLaaReference()).isEqualTo("REF456");
    assertThat(app.getLinkedApplications().get(0).getIsLead()).isFalse();
  }

  @Test
  void givenNullCaseworkerId_whenToGetAllApplicationsResponse_thenAssignedToIsNull() {
    ApplicationSummaryDomain domain =
        DataGenerator.createDefault(
            ApplicationSummaryDomainGenerator.class, b -> b.caseworkerId(null));
    PagedResultDomain<ApplicationSummaryDomain> page = new PagedResultDomain<>(List.of(domain), 1);
    GetAllApplicationsResult result = new GetAllApplicationsResult(page, 1, 10);

    ApplicationSummaryResponse body = mapper.toGetAllApplicationsResponse(result).getBody();

    assertThat(body).isNotNull();
    assertThat(body.getApplications().get(0).getAssignedTo()).isNull();
  }

  @Test
  void givenNullSubmittedAt_whenToGetAllApplicationsResponse_thenSubmittedAtIsNull() {
    ApplicationSummaryDomain domain =
        DataGenerator.createDefault(
            ApplicationSummaryDomainGenerator.class, b -> b.submittedAt(null));
    PagedResultDomain<ApplicationSummaryDomain> page = new PagedResultDomain<>(List.of(domain), 1);
    GetAllApplicationsResult result = new GetAllApplicationsResult(page, 1, 10);

    ApplicationSummaryResponse body = mapper.toGetAllApplicationsResponse(result).getBody();

    assertThat(body).isNotNull();
    assertThat(body.getApplications().get(0).getSubmittedAt()).isNull();
  }

  @Test
  void givenNullCategoryOfLaw_whenToGetAllApplicationsResponse_thenCategoryOfLawIsNull() {
    ApplicationSummaryDomain domain =
        DataGenerator.createDefault(
            ApplicationSummaryDomainGenerator.class, b -> b.categoryOfLaw(null));
    PagedResultDomain<ApplicationSummaryDomain> page = new PagedResultDomain<>(List.of(domain), 1);
    GetAllApplicationsResult result = new GetAllApplicationsResult(page, 1, 10);

    ApplicationSummaryResponse body = mapper.toGetAllApplicationsResponse(result).getBody();

    assertThat(body).isNotNull();
    assertThat(body.getApplications().get(0).getCategoryOfLaw()).isNull();
  }

  @Test
  void givenNullMatterType_whenToGetAllApplicationsResponse_thenMatterTypeIsNull() {
    ApplicationSummaryDomain domain =
        DataGenerator.createDefault(
            ApplicationSummaryDomainGenerator.class, b -> b.matterType(null));
    PagedResultDomain<ApplicationSummaryDomain> page = new PagedResultDomain<>(List.of(domain), 1);
    GetAllApplicationsResult result = new GetAllApplicationsResult(page, 1, 10);

    ApplicationSummaryResponse body = mapper.toGetAllApplicationsResponse(result).getBody();

    assertThat(body).isNotNull();
    assertThat(body.getApplications().get(0).getMatterType()).isNull();
  }

  @Test
  void givenEmptyLinkedApplications_whenToGetAllApplicationsResponse_thenLinkedIsEmpty() {
    ApplicationSummaryDomain domain =
        DataGenerator.createDefault(
            ApplicationSummaryDomainGenerator.class, b -> b.linkedApplications(List.of()));
    PagedResultDomain<ApplicationSummaryDomain> page = new PagedResultDomain<>(List.of(domain), 1);
    GetAllApplicationsResult result = new GetAllApplicationsResult(page, 1, 10);

    ApplicationSummaryResponse body = mapper.toGetAllApplicationsResponse(result).getBody();

    assertThat(body).isNotNull();
    assertThat(body.getApplications().get(0).getLinkedApplications()).isEmpty();
  }

  @Test
  void givenEmptyApplicationsPage_whenToGetAllApplicationsResponse_thenPagingHasZeroTotals() {
    PagedResultDomain<ApplicationSummaryDomain> page = new PagedResultDomain<>(List.of(), 0);
    GetAllApplicationsResult result = new GetAllApplicationsResult(page, 2, 5);

    ApplicationSummaryResponse body = mapper.toGetAllApplicationsResponse(result).getBody();

    assertThat(body).isNotNull();
    assertThat(body.getPaging().getPage()).isEqualTo(2);
    assertThat(body.getPaging().getPageSize()).isEqualTo(5);
    assertThat(body.getPaging().getTotalRecords()).isEqualTo(0);
    assertThat(body.getPaging().getItemsReturned()).isEqualTo(0);
    assertThat(body.getApplications()).isEmpty();
  }

  @Test
  void givenNullStatus_whenToGetAllApplicationsResponse_thenStatusIsNull() {
    ApplicationSummaryDomain domain =
        DataGenerator.createDefault(ApplicationSummaryDomainGenerator.class, b -> b.status(null));
    PagedResultDomain<ApplicationSummaryDomain> page = new PagedResultDomain<>(List.of(domain), 1);
    GetAllApplicationsResult result = new GetAllApplicationsResult(page, 1, 10);

    ApplicationSummaryResponse body = mapper.toGetAllApplicationsResponse(result).getBody();

    assertThat(body).isNotNull();
    assertThat(body.getApplications().get(0).getStatus()).isNull();
  }
}

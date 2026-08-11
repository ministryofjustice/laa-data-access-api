package uk.gov.justice.laa.dstew.access.query.individual;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationAddress;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationClient;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreationDetails;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataId;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadRepository;
import uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture;

class IndividualsQueryHandlerTest {

  private ApplicationReadRepository applicationRepository;
  private ApplicationDataStore applicationDataStore;
  private IndividualsQueryHandler handler;

  @BeforeEach
  void setUp() {
    applicationRepository = mock(ApplicationReadRepository.class);
    applicationDataStore = mock(ApplicationDataStore.class);
    handler = new IndividualsQueryHandler(applicationRepository, applicationDataStore);
  }

  @Test
  void givenApplications_whenQueried_thenReturnsClient() {
    UUID firstApplicationId = UUID.randomUUID();
    UUID secondApplicationId = UUID.randomUUID();
    ApplicationReadModel firstApplication = application(firstApplicationId, 2L);
    ApplicationReadModel secondApplication = application(secondApplicationId, 4L);
    ApplicationClient client =
        ApplicationClient.builder().firstName("Ada").lastName("Lovelace").build();
    when(applicationRepository.findAll()).thenReturn(List.of(firstApplication, secondApplication));
    when(applicationDataStore.getAll(anyCollection()))
        .thenReturn(
            Map.of(
                new ApplicationDataId(firstApplicationId, 2L), payload(firstApplicationId, client),
                new ApplicationDataId(secondApplicationId, 4L),
                    payload(secondApplicationId, client)));

    FindIndividualsResult result =
        handler.handle(new FindIndividualsQuery(null, "CLIENT", false, 1, 20));

    assertThat(result.client()).isNotNull();
    assertThat(result.client().getFirstName()).isEqualTo("Ada");
    assertThat(result.totalRecords()).isEqualTo(1);
    assertThat(result.page()).isEqualTo(1);
    assertThat(result.pageSize()).isEqualTo(20);
    assertThat(result.clientDetails()).isNull();
  }

  @Test
  void givenApplicationFilterAndClientDetails_whenQueried_thenReturnsEnrichedResult() {
    UUID applicationId = UUID.randomUUID();
    ApplicationReadModel application = application(applicationId, 3L);
    ApplicationClient client =
        ApplicationClient.builder()
            .firstName("Ada")
            .lastName("Lovelace")
            .lastNameAtBirth("Byron")
            .previousApplicationId("previous-id")
            .relationshipToInvolvedChildren("MOTHER")
            .appliedPreviously(true)
            .addresses(
                List.of(
                    ApplicationAddress.builder()
                        .addressLineOne("1 Main Street")
                        .postcode("SW1A 1AA")
                        .countryCode("GBR")
                        .countryName("United Kingdom")
                        .build()))
            .build();
    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
    when(applicationDataStore.getAll(anyCollection()))
        .thenReturn(
            Map.of(new ApplicationDataId(applicationId, 3L), payload(applicationId, client)));

    FindIndividualsResult result =
        handler.handle(new FindIndividualsQuery(applicationId, "CLIENT", true, 1, 20));

    verify(applicationRepository).findById(applicationId);
    assertThat(result.client()).isEqualTo(client);
    assertThat(result.clientDetails().lastNameAtBirth()).isEqualTo("Byron");
    assertThat(result.clientDetails().relationshipToInvolvedChildren()).isEqualTo("MOTHER");
    assertThat(result.clientDetails().correspondenceAddress())
        .hasSize(1)
        .first()
        .extracting(ApplicationAddress::getAddressLineOne)
        .isEqualTo("1 Main Street");
  }

  @Test
  void givenUnknownApplication_whenQueried_thenReturnsEmptyPage() {
    UUID applicationId = UUID.randomUUID();
    when(applicationRepository.findById(applicationId)).thenReturn(Optional.empty());
    when(applicationDataStore.getAll(List.of())).thenReturn(Map.of());

    FindIndividualsResult result =
        handler.handle(new FindIndividualsQuery(applicationId, null, false, 1, 20));

    assertThat(result.client()).isNull();
    assertThat(result.totalRecords()).isZero();
  }

  @Test
  void givenAbsentPaging_whenQueryCreated_thenUsesSharedDefaults() {
    FindIndividualsQuery query = new FindIndividualsQuery(null, null, false, null, null);

    assertThat(query.page()).isEqualTo(1);
    assertThat(query.pageSize()).isEqualTo(20);
  }

  @Test
  void givenNonClientType_whenQueried_thenReturnsEmpty() {
    UUID applicationId = UUID.randomUUID();
    ApplicationReadModel application = application(applicationId, 1L);
    ApplicationClient client =
        ApplicationClient.builder().firstName("Ada").lastName("Lovelace").build();
    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
    when(applicationDataStore.getAll(anyCollection()))
        .thenReturn(
            Map.of(new ApplicationDataId(applicationId, 1L), payload(applicationId, client)));

    FindIndividualsResult result =
        handler.handle(new FindIndividualsQuery(applicationId, "OTHER", false, 1, 20));

    assertThat(result.client()).isNull();
    assertThat(result.totalRecords()).isZero();
  }

  private ApplicationReadModel application(UUID id, long dataVersion) {
    return ApplicationReadModel.builder()
        .applicationId(id)
        .applicationDataVersion(dataVersion)
        .build();
  }

  private ApplicationDataPayload payload(UUID applicationId, ApplicationClient client) {
    ApplicationCreationDetails base =
        ApplicationCreatedEventFixture.applicationCreationDetails(applicationId);
    return ApplicationDataPayload.from(
        new ApplicationCreationDetails(
            base.status(),
            base.laaReference(),
            client,
            base.provider(),
            base.opponents(),
            base.allLinkedApplications(),
            base.schemaVersion(),
            base.applyApplicationId(),
            base.submittedAt(),
            base.usedDelegatedFunctions(),
            base.categoryOfLaw(),
            base.matterType(),
            base.proceedings(),
            base.serialisedRequest(),
            base.occurredAt(),
            base.leadApplicationId()));
  }
}

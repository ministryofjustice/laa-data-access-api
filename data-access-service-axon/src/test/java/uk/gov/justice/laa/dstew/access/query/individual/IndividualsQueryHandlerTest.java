package uk.gov.justice.laa.dstew.access.query.individual;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationApplicant;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreationDetails;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationIndividual;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataId;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadRepository;
import uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

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
  void givenApplications_whenQueried_thenUsesCurrentVersionsFiltersDeduplicatesAndPages() {
    UUID firstApplicationId = UUID.randomUUID();
    UUID secondApplicationId = UUID.randomUUID();
    ApplicationReadModel firstApplication = application(firstApplicationId, 2L);
    ApplicationReadModel secondApplication = application(secondApplicationId, 4L);
    ApplicationIndividual first = individual(new UUID(0, 1), "Ada", "CLIENT");
    ApplicationIndividual second = individual(new UUID(0, 2), "Grace", "CLIENT");
    ApplicationIndividual nonClient = individual(new UUID(0, 3), "Alan", "OTHER");
    when(applicationRepository.findAll()).thenReturn(List.of(firstApplication, secondApplication));
    when(applicationDataStore.getAll(anyCollection()))
        .thenReturn(
            Map.of(
                new ApplicationDataId(firstApplicationId, 2L),
                    payload(firstApplicationId, first, nonClient),
                new ApplicationDataId(secondApplicationId, 4L),
                    payload(secondApplicationId, first, second)));

    FindIndividualsResult result =
        handler.handle(new FindIndividualsQuery(null, "CLIENT", false, 2, 1));

    assertThat(result.individuals())
        .extracting(ApplicationIndividual::firstName)
        .containsExactly("Grace");
    assertThat(result.totalRecords()).isEqualTo(2);
    assertThat(result.page()).isEqualTo(2);
    assertThat(result.pageSize()).isEqualTo(1);
    assertThat(result.clientDetails()).isNull();
  }

  @Test
  void givenApplicationFilterAndClientDetails_whenQueried_thenReturnsEnrichedResult() {
    UUID applicationId = UUID.randomUUID();
    ApplicationReadModel application = application(applicationId, 3L);
    ApplicationIndividual client = individual(UUID.randomUUID(), "Ada", "CLIENT");
    ApplicationContent content =
        ApplicationContent.builder()
            .lastNameAtBirth("Byron")
            .previousApplicationId("previous-id")
            .correspondenceAddressType("HOME")
            .applicant(
                ApplicationApplicant.builder()
                    .relationshipToInvolvedChildren("MOTHER")
                    .appliedPreviously(true)
                    .addresses(List.of(Map.of("line1", "1 Main Street")))
                    .build())
            .build();
    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
    when(applicationDataStore.getAll(anyCollection()))
        .thenReturn(
            Map.of(
                new ApplicationDataId(applicationId, 3L), payload(applicationId, content, client)));

    FindIndividualsResult result =
        handler.handle(new FindIndividualsQuery(applicationId, "CLIENT", true, 1, 20));

    verify(applicationRepository).findById(applicationId);
    assertThat(result.individuals()).containsExactly(client);
    assertThat(result.clientDetails().lastNameAtBirth()).isEqualTo("Byron");
    assertThat(result.clientDetails().relationshipToInvolvedChildren()).isEqualTo("MOTHER");
    assertThat(result.clientDetails().correspondenceAddress())
        .containsExactly(Map.of("line1", "1 Main Street"));
  }

  @Test
  void givenUnknownApplication_whenQueried_thenReturnsEmptyPage() {
    UUID applicationId = UUID.randomUUID();
    when(applicationRepository.findById(applicationId)).thenReturn(Optional.empty());
    when(applicationDataStore.getAll(List.of())).thenReturn(Map.of());

    FindIndividualsResult result =
        handler.handle(new FindIndividualsQuery(applicationId, null, false, 1, 20));

    assertThat(result.individuals()).isEmpty();
    assertThat(result.totalRecords()).isZero();
  }

  @Test
  void givenAbsentPaging_whenQueryCreated_thenUsesSharedDefaults() {
    FindIndividualsQuery query = new FindIndividualsQuery(null, null, false, null, null);

    assertThat(query.page()).isEqualTo(1);
    assertThat(query.pageSize()).isEqualTo(20);
  }

  @Test
  void givenClientDetailsWithoutApplication_whenQueryCreated_thenRejectsCombination() {
    assertThatThrownBy(() -> new FindIndividualsQuery(null, "CLIENT", true, 1, 20))
        .isInstanceOf(ValidationException.class)
        .satisfies(
            exception ->
                assertThat(((ValidationException) exception).errors())
                    .containsExactly(
                        "Application ID is required when included data is CLIENT_DETAILS"));
  }

  private ApplicationReadModel application(UUID id, long dataVersion) {
    return ApplicationReadModel.builder()
        .applicationId(id)
        .applicationDataVersion(dataVersion)
        .build();
  }

  private ApplicationIndividual individual(UUID id, String firstName, String type) {
    return new ApplicationIndividual(
        id, firstName, "Person", LocalDate.of(2000, 1, 1), Map.of("key", "value"), type);
  }

  private ApplicationDataPayload payload(UUID applicationId, ApplicationIndividual... individuals) {
    return payload(applicationId, null, individuals);
  }

  private ApplicationDataPayload payload(
      UUID applicationId, ApplicationContent content, ApplicationIndividual... individuals) {
    ApplicationCreationDetails base =
        ApplicationCreatedEventFixture.applicationCreationDetails(applicationId);
    return ApplicationDataPayload.from(
        new ApplicationCreationDetails(
            base.status(),
            base.laaReference(),
            content,
            List.of(individuals),
            base.schemaVersion(),
            base.applicationType(),
            base.applyApplicationId(),
            base.submittedAt(),
            base.officeCode(),
            base.usedDelegatedFunctions(),
            base.categoryOfLaw(),
            base.matterType(),
            base.proceedings(),
            base.serialisedRequest(),
            base.occurredAt(),
            base.leadApplicationId()));
  }
}

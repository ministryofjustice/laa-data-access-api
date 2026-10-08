package uk.gov.justice.laa.dstew.access.query.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreationDetails;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataId;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadModel;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadRepository;
import uk.gov.justice.laa.dstew.access.query.application.listindex.ApplicationListIndexReadModel;
import uk.gov.justice.laa.dstew.access.query.application.listindex.ApplicationListIndexReadRepository;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityReadRepository;

@ExtendWith(MockitoExtension.class)
class ApplicationReadModelAssemblerTest {

  @Mock private ApplicationDataStore applicationDataStore;
  @Mock private LinkedApplicationGroupReadRepository groupReadRepository;
  @Mock private ApplicationListIndexReadRepository listIndexRepository;
  @Mock private PriorAuthorityReadRepository priorAuthorityReadRepository;
  @Mock private ApplicationDataPayload payloadWithoutProvider;
  @InjectMocks private ApplicationReadModelAssembler assembler;

  @Test
  void givenRowsWithMissingData_whenHydrated_thenDropsMissingAndPreservesOrder() {
    var first = ApplicationReadModel.builder().applicationId(UUID.randomUUID()).build();
    var missing = ApplicationReadModel.builder().applicationId(UUID.randomUUID()).build();
    var last = ApplicationReadModel.builder().applicationId(UUID.randomUUID()).build();
    var firstId = new ApplicationDataId(first.getApplicationId(), 0L);
    var missingId = new ApplicationDataId(missing.getApplicationId(), 0L);
    var lastId = new ApplicationDataId(last.getApplicationId(), 0L);
    when(applicationDataStore.getAll(List.of(firstId, missingId, lastId)))
        .thenReturn(
            Map.of(
                firstId, payload(first.getApplicationId()),
                lastId, payload(last.getApplicationId())));

    var hydrated = assembler.hydrate(List.of(first, missing, last));

    assertThat(hydrated).containsExactly(first, last);
    assertThat(first.getLaaReference()).isEqualTo("LAA-123");
  }

  @Test
  void givenHydratedPayload_whenAssembled_thenKeepsPersistedSubmittedAtAndCodes() {
    UUID applicationId = UUID.randomUUID();
    var eventSubmittedAt = java.time.Instant.parse("2026-08-01T10:00:00Z");
    var application =
        ApplicationReadModel.builder()
            .applicationId(applicationId)
            .applicationDataVersion(0L)
            .submittedAt(eventSubmittedAt)
            .build();
    Proceeding proceeding =
        Proceeding.builder()
            .id(UUID.randomUUID())
            .leadProceeding(true)
            .code("SE003")
            .description("Care order")
            .categoryOfLawCode("MAT")
            .matterTypeCode("KPBLW")
            .build();
    ApplicationDataPayload base = payload(applicationId);
    ApplicationDataPayload data =
        base.withApplicationUpdate(
            base.client(),
            base.provider(),
            base.opponents(),
            java.time.Instant.parse("2026-07-14T12:30:00Z"),
            base.usedDelegatedFunctions(),
            "Family",
            "special children act (SCA)",
            "MAT",
            "KPBLW",
            List.of(proceeding),
            "{}",
            false);
    ApplicationDataId dataId = new ApplicationDataId(applicationId, 0L);
    when(applicationDataStore.getAll(List.of(dataId))).thenReturn(Map.of(dataId, data));

    ApplicationReadModel hydrated = assembler.hydrate(application).orElseThrow();

    assertThat(hydrated.getSubmittedAt()).isEqualTo(eventSubmittedAt);
    assertThat(hydrated.getCategoryOfLawCode()).isEqualTo("MAT");
    assertThat(hydrated.getMatterTypeCode()).isEqualTo("KPBLW");
  }

  @Test
  void givenLinkedMembers_whenAssemblingDetail_thenReturnsMemberDetails() {
    UUID applicationId = UUID.randomUUID();
    UUID linkedApplicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    var application =
        ApplicationReadModel.builder().applicationId(applicationId).linkedGroupId(groupId).build();
    var group =
        LinkedApplicationGroupReadModel.builder()
            .groupId(groupId)
            .leadApplicationId(applicationId)
            .memberIds(List.of(applicationId, linkedApplicationId))
            .build();
    when(groupReadRepository.findById(groupId)).thenReturn(Optional.of(group));
    when(priorAuthorityReadRepository.findAllByApplicationIdIn(List.of(applicationId)))
        .thenReturn(List.of());
    when(listIndexRepository.findAllById(List.of(applicationId, linkedApplicationId)))
        .thenReturn(
            List.of(
                ApplicationListIndexReadModel.builder()
                    .applicationId(applicationId)
                    .laaReference("LAA-APPLICATION")
                    .clientFirstName("Ada")
                    .clientLastName("Lovelace")
                    .build(),
                ApplicationListIndexReadModel.builder()
                    .applicationId(linkedApplicationId)
                    .laaReference("LAA-LINKED")
                    .clientFirstName("Grace")
                    .clientLastName("Hopper")
                    .build()));

    var result = assembler.assembleDetail(application);

    assertThat(result.application()).isSameAs(application);
    assertThat(result.linkedGroup()).isSameAs(group);
    assertThat(result.linkedMemberDetails())
        .containsOnly(
            entry(
                applicationId,
                new LinkedApplicationMemberDetails("LAA-APPLICATION", "Ada", "Lovelace")),
            entry(
                linkedApplicationId,
                new LinkedApplicationMemberDetails("LAA-LINKED", "Grace", "Hopper")));
  }

  @Test
  void givenGroupsSharingMembers_whenFetchingLinkedMemberDetails_thenLoadsEachMemberOnce() {
    UUID sharedId = UUID.randomUUID();
    UUID firstId = UUID.randomUUID();
    UUID secondId = UUID.randomUUID();
    var groups =
        List.of(
            LinkedApplicationGroupReadModel.builder().memberIds(List.of(firstId, sharedId)).build(),
            LinkedApplicationGroupReadModel.builder()
                .memberIds(List.of(sharedId, secondId))
                .build());
    when(listIndexRepository.findAllById(List.of(firstId, sharedId, secondId)))
        .thenReturn(
            List.of(
                ApplicationListIndexReadModel.builder()
                    .applicationId(firstId)
                    .laaReference("LAA-FIRST")
                    .clientFirstName("First")
                    .clientLastName("Member")
                    .build(),
                ApplicationListIndexReadModel.builder()
                    .applicationId(sharedId)
                    .laaReference("LAA-SHARED")
                    .clientFirstName("Shared")
                    .clientLastName("Member")
                    .build(),
                ApplicationListIndexReadModel.builder()
                    .applicationId(secondId)
                    .laaReference("LAA-SECOND")
                    .clientFirstName("Second")
                    .clientLastName("Member")
                    .build()));

    assertThat(assembler.fetchLinkedMemberDetails(groups))
        .contains(
            entry(firstId, new LinkedApplicationMemberDetails("LAA-FIRST", "First", "Member")),
            entry(sharedId, new LinkedApplicationMemberDetails("LAA-SHARED", "Shared", "Member")),
            entry(secondId, new LinkedApplicationMemberDetails("LAA-SECOND", "Second", "Member")));
  }

  @Test
  void givenNoGroups_whenFetchingLinkedMemberDetails_thenSkipsLookup() {
    assertThat(assembler.fetchLinkedMemberDetails(List.of())).isEmpty();
    verifyNoInteractions(listIndexRepository);
  }

  @Test
  void givenMissingLinkedMemberDetails_whenFetching_thenRejectsIncompleteMember() {
    UUID memberId = UUID.randomUUID();
    var group = LinkedApplicationGroupReadModel.builder().memberIds(List.of(memberId)).build();
    when(listIndexRepository.findAllById(List.of(memberId))).thenReturn(List.of());

    assertThatThrownBy(() -> assembler.fetchLinkedMemberDetails(List.of(group)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(memberId.toString());
  }

  @Test
  void givenLinkedMemberWithoutRequiredName_whenFetching_thenRejectsIncompleteMember() {
    UUID memberId = UUID.randomUUID();
    var group = LinkedApplicationGroupReadModel.builder().memberIds(List.of(memberId)).build();
    when(listIndexRepository.findAllById(List.of(memberId)))
        .thenReturn(
            List.of(
                ApplicationListIndexReadModel.builder()
                    .applicationId(memberId)
                    .laaReference("LAA-MEMBER")
                    .clientLastName("Member")
                    .build()));

    assertThatThrownBy(() -> assembler.fetchLinkedMemberDetails(List.of(group)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(memberId.toString());
  }

  @Test
  void givenStandaloneApplication_whenAssemblingDetail_thenSkipsGroupLookups() {
    UUID applicationId = UUID.randomUUID();
    var application = ApplicationReadModel.builder().applicationId(applicationId).build();
    when(priorAuthorityReadRepository.findAllByApplicationIdIn(List.of(applicationId)))
        .thenReturn(List.of());

    var result = assembler.assembleDetail(application);

    assertThat(result.linkedGroup()).isNull();
    assertThat(result.linkedMemberDetails()).isEmpty();
    verifyNoInteractions(groupReadRepository, listIndexRepository);
  }

  @Test
  void givenApplicationsSharingGroup_whenFetchingGroups_thenLoadsEachGroupOnce() {
    UUID groupId = UUID.randomUUID();
    var group = LinkedApplicationGroupReadModel.builder().groupId(groupId).build();
    var applications =
        List.of(
            ApplicationReadModel.builder().linkedGroupId(groupId).build(),
            ApplicationReadModel.builder().linkedGroupId(groupId).build(),
            ApplicationReadModel.builder().build());
    when(groupReadRepository.findAllById(List.of(groupId))).thenReturn(List.of(group));

    assertThat(assembler.fetchGroups(applications)).containsExactly(entry(groupId, group));
  }

  @Test
  void givenMissingDataOrProvider_whenReadingOfficeCode_thenReturnsNull() {
    assertThat(ApplicationReadModelAssembler.officeCode(null)).isNull();
    assertThat(ApplicationReadModelAssembler.officeCode(payloadWithoutProvider)).isNull();
  }

  private static ApplicationDataPayload payload(UUID applicationId) {
    return ApplicationDataPayload.from(applicationCreationDetails(applicationId));
  }
}

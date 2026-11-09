package uk.gov.justice.laa.dstew.access.query.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreationDetails;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadModel;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadRepository;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityReadRepository;
import uk.gov.justice.laa.dstew.access.query.application.summary.ApplicationSummaries;
import uk.gov.justice.laa.dstew.access.query.application.summary.ApplicationSummaryReadModel;

@ExtendWith(MockitoExtension.class)
class ApplicationReadModelAssemblerTest {

  @Mock private LinkedApplicationGroupReadRepository groupReadRepository;
  @Mock private ApplicationSummaries applicationSummaries;
  @Mock private PriorAuthorityReadRepository priorAuthorityReadRepository;
  @InjectMocks private ApplicationReadModelAssembler assembler;

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
    when(applicationSummaries.findByIds(List.of(applicationId, linkedApplicationId)))
        .thenReturn(
            byId(
                List.of(
                    ApplicationSummaryReadModel.builder()
                        .applicationId(applicationId)
                        .laaReference("LAA-APPLICATION")
                        .status("APPLICATION_SUBMITTED")
                        .clientFirstName("Ada")
                        .clientLastName("Lovelace")
                        .build(),
                    ApplicationSummaryReadModel.builder()
                        .applicationId(linkedApplicationId)
                        .laaReference("LAA-LINKED")
                        .status("APPLICATION_SUBMITTED")
                        .clientFirstName("Grace")
                        .clientLastName("Hopper")
                        .build())));

    var result = assembler.assembleDetail(application);

    assertThat(result.application()).isSameAs(application);
    assertThat(result.linkedGroup()).isSameAs(group);
    assertThat(result.linkedMemberDetails())
        .containsOnly(
            entry(
                applicationId,
                new LinkedApplicationMemberDetails(
                    "LAA-APPLICATION", "APPLICATION_SUBMITTED", "Ada", "Lovelace")),
            entry(
                linkedApplicationId,
                new LinkedApplicationMemberDetails(
                    "LAA-LINKED", "APPLICATION_SUBMITTED", "Grace", "Hopper")));
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
    when(applicationSummaries.findByIds(List.of(firstId, sharedId, secondId)))
        .thenReturn(
            byId(
                List.of(
                    ApplicationSummaryReadModel.builder()
                        .applicationId(firstId)
                        .laaReference("LAA-FIRST")
                        .status("APPLICATION_SUBMITTED")
                        .clientFirstName("First")
                        .clientLastName("Member")
                        .build(),
                    ApplicationSummaryReadModel.builder()
                        .applicationId(sharedId)
                        .laaReference("LAA-SHARED")
                        .status("APPLICATION_SUBMITTED")
                        .clientFirstName("Shared")
                        .clientLastName("Member")
                        .build(),
                    ApplicationSummaryReadModel.builder()
                        .applicationId(secondId)
                        .laaReference("LAA-SECOND")
                        .status("APPLICATION_SUBMITTED")
                        .clientFirstName("Second")
                        .clientLastName("Member")
                        .build())));

    assertThat(assembler.fetchLinkedMemberDetails(groups))
        .contains(
            entry(
                firstId,
                new LinkedApplicationMemberDetails(
                    "LAA-FIRST", "APPLICATION_SUBMITTED", "First", "Member")),
            entry(
                sharedId,
                new LinkedApplicationMemberDetails(
                    "LAA-SHARED", "APPLICATION_SUBMITTED", "Shared", "Member")),
            entry(
                secondId,
                new LinkedApplicationMemberDetails(
                    "LAA-SECOND", "APPLICATION_SUBMITTED", "Second", "Member")));
  }

  @Test
  void givenNoGroups_whenFetchingLinkedMemberDetails_thenSkipsLookup() {
    assertThat(assembler.fetchLinkedMemberDetails(List.of())).isEmpty();
    verifyNoInteractions(applicationSummaries);
  }

  @Test
  void givenMissingLinkedMemberDetails_whenFetching_thenRejectsIncompleteMember() {
    UUID memberId = UUID.randomUUID();
    var group = LinkedApplicationGroupReadModel.builder().memberIds(List.of(memberId)).build();
    when(applicationSummaries.findByIds(List.of(memberId))).thenReturn(byId(List.of()));

    assertThatThrownBy(() -> assembler.fetchLinkedMemberDetails(List.of(group)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(memberId.toString());
  }

  @Test
  void givenLinkedMemberWithoutRequiredName_whenFetching_thenRejectsIncompleteMember() {
    UUID memberId = UUID.randomUUID();
    var group = LinkedApplicationGroupReadModel.builder().memberIds(List.of(memberId)).build();
    when(applicationSummaries.findByIds(List.of(memberId)))
        .thenReturn(
            byId(
                List.of(
                    ApplicationSummaryReadModel.builder()
                        .applicationId(memberId)
                        .laaReference("LAA-MEMBER")
                        .clientLastName("Member")
                        .build())));

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
    verifyNoInteractions(groupReadRepository, applicationSummaries);
  }

  @Test
  void givenApplicationsSharingGroup_whenFetchingGroups_thenLoadsEachGroupOnce() {
    UUID groupId = UUID.randomUUID();
    var group = LinkedApplicationGroupReadModel.builder().groupId(groupId).build();
    when(groupReadRepository.findAllById(List.of(groupId))).thenReturn(List.of(group));

    assertThat(assembler.fetchGroups(Arrays.asList(groupId, groupId, null)))
        .containsExactly(entry(groupId, group));
  }

  private static ApplicationDataPayload payload(UUID applicationId) {
    return ApplicationDataPayload.from(applicationCreationDetails(applicationId));
  }

  private static Map<UUID, ApplicationSummaryReadModel> byId(
      List<ApplicationSummaryReadModel> summaries) {
    return summaries.stream()
        .collect(
            Collectors.toMap(ApplicationSummaryReadModel::getApplicationId, Function.identity()));
  }
}

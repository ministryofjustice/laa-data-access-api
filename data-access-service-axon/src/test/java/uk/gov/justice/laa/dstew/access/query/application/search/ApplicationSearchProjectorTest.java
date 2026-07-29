package uk.gov.justice.laa.dstew.access.query.application.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreationDetails;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationSearchView;
import uk.gov.justice.laa.dstew.access.query.application.FindAllApplicationsQuery;
import uk.gov.justice.laa.dstew.access.query.application.FindAllApplicationsResult;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadRepository;

class ApplicationSearchProjectorTest {

  private ApplicationSearchRepository repository;
  private ApplicationLinkSearchRepository linkRepository;
  private LinkedApplicationGroupReadRepository groupReadRepository;
  private ApplicationDataStore applicationDataStore;
  private ApplicationSearchProjector projector;

  @BeforeEach
  void setUp() {
    repository = mock(ApplicationSearchRepository.class);
    linkRepository = mock(ApplicationLinkSearchRepository.class);
    groupReadRepository = mock(LinkedApplicationGroupReadRepository.class);
    applicationDataStore = mock(ApplicationDataStore.class);
    projector =
        new ApplicationSearchProjector(
            repository, linkRepository, groupReadRepository, applicationDataStore);
  }

  @Test
  void givenCreatedEvent_whenHandled_thenHydratesAndPersistsSearchView() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent event =
        new ApplicationCreatedEvent(
            applicationId,
            1L,
            "fp",
            "SUBMITTED",
            1,
            "INITIAL",
            null,
            Instant.parse("2026-07-22T10:00:00Z"),
            applicationId,
            List.of(applicationId));
    ApplicationDataPayload payload =
        ApplicationDataPayload.from(applicationCreationDetails(applicationId));

    when(repository.findById(applicationId)).thenReturn(Optional.empty());
    when(applicationDataStore.get(applicationId, 1L)).thenReturn(payload);

    projector.on(event, 3L);

    verify(repository).save(any(ApplicationSearchView.class));
  }

  @Test
  void givenSearchRows_whenQueryHandled_thenReturnsMappedPageWithLinkedGroups() {
    UUID leadId = UUID.randomUUID();
    UUID memberId = UUID.randomUUID();

    ApplicationSearchView member =
        ApplicationSearchView.builder()
            .applicationId(memberId)
            .status("GRANTED")
            .laaReference("LAA-1")
            .matterType("IMMIGRATION")
            .categoryOfLaw("IMMIGRATION")
            .submittedAt(Instant.parse("2026-07-20T10:00:00Z"))
            .modifiedAt(Instant.parse("2026-07-21T10:00:00Z"))
            .leadApplicationId(leadId)
            .isLead(false)
            .clientFirstName("Alex")
            .clientLastName("Smith")
            .build();

    when(repository.findAll(
            any(Specification.class), any(org.springframework.data.domain.Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(member)));
    when(linkRepository.findAllByApplicationIdIn(List.of(memberId)))
        .thenReturn(
            List.of(
                ApplicationLinkSearchView.builder()
                    .id(new ApplicationLinkSearchView.ApplicationLinkId(leadId, memberId))
                    .build()));
    when(linkRepository.findAllByLeadApplicationIdIn(List.of(leadId)))
        .thenReturn(
            List.of(
                ApplicationLinkSearchView.builder()
                    .id(new ApplicationLinkSearchView.ApplicationLinkId(leadId, leadId))
                    .build(),
                ApplicationLinkSearchView.builder()
                    .id(new ApplicationLinkSearchView.ApplicationLinkId(leadId, memberId))
                    .build()));

    FindAllApplicationsResult result =
        projector.handle(
            new FindAllApplicationsQuery(
                null, null, null, null, null, null, null, null, null, null, 1, 20));

    assertThat(result.applications()).hasSize(1);
    assertThat(result.applications().get(0).getApplicationId()).isEqualTo(memberId);
    assertThat(result.groupsByLeadId()).containsKey(leadId);
    assertThat(result.groupsByLeadId().get(leadId).getMemberIds())
        .containsExactlyInAnyOrder(leadId, memberId);
    verify(repository)
        .findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class));
    verify(linkRepository).findAllByApplicationIdIn(eq(List.of(memberId)));
    verify(linkRepository).findAllByLeadApplicationIdIn(eq(List.of(leadId)));
  }
}

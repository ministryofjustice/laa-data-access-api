package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import uk.gov.justice.laa.dstew.access.testutils.LinkedGroupTestFixture;

class LinkedGroupJustificationHandlerTest {
  private final LinkedGroupJustificationStore store =
      Mockito.mock(LinkedGroupJustificationStore.class);
  private final AxonTestFixture fixture = LinkedGroupTestFixture.linkedGroupFixture(store);
  private final UUID groupId = UUID.randomUUID();
  private final UUID leadId = UUID.randomUUID();
  private final UUID memberId = UUID.randomUUID();
  private final Instant time = Instant.parse("2026-07-15T08:00:00Z");

  @AfterEach
  void close() {
    fixture.stop();
  }

  @Test
  void givenCurrentLeadWithJustification_whenRequestedAgain_thenStoresNothing() {
    fixture
        .given()
        .events(created())
        .when()
        .command(new ChangeLinkedGroupLeadCommand(groupId, leadId, 0, time, "No-op"))
        .then()
        .noEvents();
    verifyNoInteractions(store);
  }

  @Test
  void givenExistingMemberWithJustification_whenAddedAgain_thenStoresNothing() {
    fixture
        .given()
        .events(created())
        .when()
        .command(new AddApplicationToLinkedGroupCommand(groupId, memberId, 0, time, "No-op"))
        .then()
        .noEvents();
    verifyNoInteractions(store);
  }

  @Test
  void givenEstablishedGroupWithJustification_whenEstablishedAgain_thenStoresNothing() {
    fixture
        .given()
        .events(created())
        .when()
        .command(
            new EstablishLinkedApplicationGroupCommand(groupId, leadId, memberId, time, "No-op"))
        .then()
        .noEvents();
    verifyNoInteractions(store);
  }

  @Test
  void givenLeadChange_whenAccepted_thenEventReferencesStoredJustification() {
    var id = UUID.randomUUID();
    when(store.save(groupId, "Lead reason", time)).thenReturn(id);
    fixture
        .given()
        .events(created())
        .when()
        .command(new ChangeLinkedGroupLeadCommand(groupId, memberId, 0, time, "Lead reason"))
        .then()
        .events(
            new LinkedApplicationGroupLeadChangedEvent(
                groupId, leadId, memberId, 1, time, List.of(leadId, memberId), id));
    Mockito.verify(store).save(groupId, "Lead reason", time);
    assertThat(created().justificationId()).isNull();
  }

  private LinkedApplicationGroupCreatedEvent created() {
    return new LinkedApplicationGroupCreatedEvent(groupId, leadId, List.of(leadId, memberId), time);
  }
}

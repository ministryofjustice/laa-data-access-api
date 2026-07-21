package uk.gov.justice.laa.dstew.access.command.workallocation;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.exception.ConflictException;

class WorkAllocationTest {

  private static final Instant FIXED_NOW = Instant.parse("2026-07-15T08:00:00Z");

  private AggregateTestFixture<WorkAllocation> fixture;

  @BeforeEach
  void setUp() {
    fixture = new AggregateTestFixture<>(WorkAllocation.class);
    fixture.registerInjectableResource(Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
  }

  @Test
  void givenNoAllocation_whenAssigned_thenCreatesStreamAndPublishesAssignedEvent() {
    UUID workItemId = UUID.randomUUID();
    UUID allocationId = AllocationId.forWorkItem(workItemId);
    UUID caseworkerId = UUID.randomUUID();

    fixture
        .givenNoPriorActivity()
        .when(new AssignCaseworkerCommand(allocationId, workItemId, caseworkerId))
        .expectEvents(
            new CaseworkerAssignedEvent(allocationId, workItemId, caseworkerId, FIXED_NOW));
  }

  @Test
  void givenAssigned_whenAssignedSameCaseworker_thenIdempotentWithNoEvent() {
    UUID workItemId = UUID.randomUUID();
    UUID allocationId = AllocationId.forWorkItem(workItemId);
    UUID caseworkerId = UUID.randomUUID();

    fixture
        .given(new CaseworkerAssignedEvent(allocationId, workItemId, caseworkerId, FIXED_NOW))
        .when(new AssignCaseworkerCommand(allocationId, workItemId, caseworkerId))
        .expectNoEvents();
  }

  @Test
  void givenAssigned_whenAssignedDifferentCaseworker_thenRejectsWithConflict() {
    UUID workItemId = UUID.randomUUID();
    UUID allocationId = AllocationId.forWorkItem(workItemId);

    fixture
        .given(new CaseworkerAssignedEvent(allocationId, workItemId, UUID.randomUUID(), FIXED_NOW))
        .when(new AssignCaseworkerCommand(allocationId, workItemId, UUID.randomUUID()))
        .expectException(ConflictException.class);
  }

  @Test
  void givenAssigned_whenUnassigned_thenPublishesUnassignedEvent() {
    UUID workItemId = UUID.randomUUID();
    UUID allocationId = AllocationId.forWorkItem(workItemId);

    fixture
        .given(new CaseworkerAssignedEvent(allocationId, workItemId, UUID.randomUUID(), FIXED_NOW))
        .when(new UnassignCaseworkerCommand(allocationId, workItemId))
        .expectEvents(new CaseworkerUnassignedEvent(allocationId, workItemId, FIXED_NOW));
  }

  @Test
  void givenUnassigned_whenUnassigned_thenRejectsWithConflict() {
    UUID workItemId = UUID.randomUUID();
    UUID allocationId = AllocationId.forWorkItem(workItemId);

    fixture
        .given(
            new CaseworkerAssignedEvent(allocationId, workItemId, UUID.randomUUID(), FIXED_NOW),
            new CaseworkerUnassignedEvent(allocationId, workItemId, FIXED_NOW))
        .when(new UnassignCaseworkerCommand(allocationId, workItemId))
        .expectException(ConflictException.class);
  }

  @Test
  void givenNoAllocation_whenUnassigned_thenRejectsWithAggregateNotFound() {
    UUID workItemId = UUID.randomUUID();

    fixture
        .givenNoPriorActivity()
        .when(new UnassignCaseworkerCommand(AllocationId.forWorkItem(workItemId), workItemId))
        .expectException(AggregateNotFoundException.class);
  }
}

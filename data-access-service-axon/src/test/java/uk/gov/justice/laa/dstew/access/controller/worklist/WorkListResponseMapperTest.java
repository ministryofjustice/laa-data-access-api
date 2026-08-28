package uk.gov.justice.laa.dstew.access.controller.worklist;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.query.worklist.FindWorkListItemsResult;
import uk.gov.justice.laa.dstew.access.query.worklist.WorkListItemReadModel;

/** Unit tests for the public work-list response mapper. */
class WorkListResponseMapperTest {

  @Test
  void mapsBusinessIdentityAssignmentContextAndPaging() {
    UUID itemId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    WorkListItemReadModel item =
        new WorkListItemReadModel(
            WorkItemType.PRIOR_AUTHORITY,
            itemId,
            applicationId,
            applicationId,
            Instant.parse("2026-08-28T10:00:00Z"),
            4L,
            9L);
    item.setAssigneeId(caseworkerId);
    assertThat(item.getAssignmentVersion()).isZero();
    item.setAssignmentVersion(3L);
    item.setLaaReference("LAA-123");
    item.setSubmittedAt(Instant.parse("2026-08-28T09:00:00Z"));

    var response =
        new WorkListResponseMapper()
            .toResponse(new FindWorkListItemsResult(List.of(item), 1L, 2, 10));

    assertThat(response.getBody().getPaging().getPage()).isEqualTo(2);
    assertThat(response.getBody().getPaging().getPageSize()).isEqualTo(10);
    assertThat(response.getBody().getPaging().getTotalRecords()).isEqualTo(1);
    assertThat(response.getBody().getItems()).singleElement().satisfies(mapped -> {
      assertThat(mapped.getItemId()).isEqualTo(itemId);
      assertThat(mapped.getItemType().getValue()).isEqualTo("PRIOR_AUTHORITY");
      assertThat(mapped.getApplicationId()).isEqualTo(applicationId);
      assertThat(mapped.getParentApplicationId()).isEqualTo(applicationId);
      assertThat(mapped.getAssignedTo()).isEqualTo(caseworkerId);
      assertThat(mapped.getAssignmentVersion()).isEqualTo(3L);
      assertThat(mapped.getAssignmentBoundaryType().getValue()).isEqualTo("DIRECT");
      assertThat(mapped.getLaaReference()).isEqualTo("LAA-123");
    });
  }
}



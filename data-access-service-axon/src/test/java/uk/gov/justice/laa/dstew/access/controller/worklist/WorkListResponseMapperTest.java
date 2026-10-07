package uk.gov.justice.laa.dstew.access.controller.worklist;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.query.worklist.FindWorkListItemsResult;
import uk.gov.justice.laa.dstew.access.query.worklist.WorkListItemReadModel;

/** Unit tests for the public work-list response mapper. */
class WorkListResponseMapperTest {

  @Test
  void mapsWorkItemIdentityParentApplicationAssignmentContextAndPaging() {
    UUID itemId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    WorkListItemReadModel item =
        new WorkListItemReadModel(
            WorkItemType.PRIOR_AUTHORITY,
            itemId,
            applicationId,
            Instant.parse("2026-08-28T10:00:00Z"),
            4L,
            9L);
    item.setAssigneeId(caseworkerId);
    assertThat(item.getAssignmentVersion()).isZero();
    item.setAssignmentVersion(3L);
    item.setLaaReference("LAA-123");
    item.setReadyAt(Instant.parse("2026-08-28T09:00:00Z"));
    item.setPriorAuthorityType("EXPERT");
    item.setExpertType("Pathologist");

    var response =
        new WorkListResponseMapper()
            .toResponse(new FindWorkListItemsResult(List.of(item), 1L, 2, 10));

    assertThat(response.getBody().getPaging().getPage()).isEqualTo(2);
    assertThat(response.getBody().getPaging().getPageSize()).isEqualTo(10);
    assertThat(response.getBody().getPaging().getTotalRecords()).isEqualTo(1);
    assertThat(response.getBody().getItems())
        .singleElement()
        .satisfies(
            mapped -> {
              assertThat(mapped.getItemId()).isEqualTo(itemId);
              assertThat(mapped.getItemType().getValue()).isEqualTo("PRIOR_AUTHORITY");
              assertThat(mapped.getParentApplicationId()).isEqualTo(applicationId);
              assertThat(mapped.getAssignedTo()).isEqualTo(caseworkerId);
              assertThat(mapped.getAssignmentVersion()).isEqualTo(3L);
              assertThat(mapped.getAssignmentBoundaryType().getValue()).isEqualTo("DIRECT");
              assertThat(mapped.getReadyAt())
                  .isEqualTo(Instant.parse("2026-08-28T09:00:00Z").atOffset(ZoneOffset.UTC));
              assertThat(mapped.getLaaReference()).isEqualTo("LAA-123");
              assertThat(mapped.getPriorAuthorityType()).isEqualTo(PriorAuthorityType.EXPERT);
              assertThat(mapped.getExpertType()).isEqualTo("Pathologist");
            });
  }

  @Test
  void mapsApplicationOnlySummaryFields() {
    WorkListItemReadModel item =
        new WorkListItemReadModel(
            WorkItemType.APPLICATION,
            UUID.randomUUID(),
            null,
            Instant.parse("2026-09-01T10:00:00Z"),
            1L,
            1L);
    item.setUsedDelegatedFunctions(true);
    item.setCategoryOfLaw("Family");
    item.setCategoryOfLawCode("FAM");
    item.setMatterTypes(List.of("Special Children Act"));
    item.setMatterTypeCodes(List.of("SCA"));
    item.setApplicationStatus("APPLICATION_SUBMITTED");

    var response =
        new WorkListResponseMapper()
            .toResponse(new FindWorkListItemsResult(List.of(item), 1L, 1, 20));

    assertThat(response.getBody().getItems())
        .singleElement()
        .satisfies(
            mapped -> {
              assertThat(mapped.getUsedDelegatedFunctions()).isTrue();
              assertThat(mapped.getCategoryOfLaw()).isEqualTo("Family");
              assertThat(mapped.getCategoryOfLawCode()).isEqualTo("FAM");
              assertThat(mapped.getMatterTypes()).containsExactly("Special Children Act");
              assertThat(mapped.getMatterTypeCodes()).containsExactly("SCA");
              assertThat(mapped.getReadyAt())
                  .isEqualTo(Instant.parse("2026-09-01T10:00:00Z").atOffset(ZoneOffset.UTC));
              assertThat(mapped.getApplicationStatus())
                  .isEqualTo(ApplicationStatus.APPLICATION_SUBMITTED);
              assertThat(mapped.getPriorAuthorityType()).isNull();
              assertThat(mapped.getExpertType()).isNull();
            });
  }

  @Test
  void givenUnknownLegalCategories_whenMapped_thenReturnsStringsAndAlignedCodes() {
    WorkListItemReadModel item =
        new WorkListItemReadModel(
            WorkItemType.APPLICATION,
            UUID.randomUUID(),
            null,
            Instant.parse("2026-09-01T10:00:00Z"),
            1L,
            1L);
    item.setCategoryOfLaw("Unexpected category");
    item.setCategoryOfLawCode("CAT-991");
    item.setMatterTypes(List.of("Unexpected matter"));
    item.setMatterTypeCodes(List.of("MAT-123"));

    var mapped =
        new WorkListResponseMapper()
            .toResponse(new FindWorkListItemsResult(List.of(item), 1L, 1, 20))
            .getBody()
            .getItems()
            .getFirst();

    assertThat(mapped.getCategoryOfLaw()).isEqualTo("Unexpected category");
    assertThat(mapped.getCategoryOfLawCode()).isEqualTo("CAT-991");
    assertThat(mapped.getMatterTypes()).containsExactly("Unexpected matter");
    assertThat(mapped.getMatterTypeCodes()).containsExactly("MAT-123");
  }

  @Test
  void givenPriorAuthority_whenMapped_thenOmitsCategoryButKeepsParentMatterPairs() {
    WorkListItemReadModel item =
        new WorkListItemReadModel(
            WorkItemType.PRIOR_AUTHORITY,
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instant.parse("2026-09-01T10:00:00Z"),
            1L,
            1L);
    item.setCategoryOfLaw("Family");
    item.setCategoryOfLawCode("FAM");
    item.setMatterTypes(List.of("First", "Second"));
    item.setMatterTypeCodes(List.of("M-1", "M-2"));

    var mapped =
        new WorkListResponseMapper()
            .toResponse(new FindWorkListItemsResult(List.of(item), 1L, 1, 20))
            .getBody()
            .getItems()
            .getFirst();

    assertThat(mapped.getCategoryOfLaw()).isNull();
    assertThat(mapped.getCategoryOfLawCode()).isNull();
    assertThat(mapped.getMatterTypes()).containsExactly("First", "Second");
    assertThat(mapped.getMatterTypeCodes()).containsExactly("M-1", "M-2");
  }
}

package uk.gov.justice.laa.dstew.access.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;

public class ApplicationSummaryMapperTest {

  private final ApplicationSummaryMapper applicationSummaryMapper = Mappers.getMapper(ApplicationSummaryMapper.class);

  @Test
  void givenApplicationSummaryEntity_whenToApplicationSummary_thenMapsFieldsCorrectly() {

    ApplicationSummaryEntity expectedApplicationSummaryEntity = ApplicationSummaryEntity.builder()
                    .id(UUID.randomUUID())
                    .createdAt(Instant.now())
                    .modifiedAt(Instant.now())
                    .laaReference("ref1")
                    .status(ApplicationStatus.IN_PROGRESS)
                    .build();

    ApplicationSummary actualApplicationSummary = applicationSummaryMapper
            .toApplicationSummary(expectedApplicationSummaryEntity);

    assertThat(actualApplicationSummary).isNotNull();
    assertThat(actualApplicationSummary.getApplicationId())
            .isEqualTo(expectedApplicationSummaryEntity.getId());
    assertThat(actualApplicationSummary.getLaaReference())
            .isEqualTo(expectedApplicationSummaryEntity.getLaaReference());
    assertThat(actualApplicationSummary.getApplicationStatus())
            .isEqualTo(expectedApplicationSummaryEntity.getStatus());
    assertThat(actualApplicationSummary.getModifiedAt())
            .isEqualTo(expectedApplicationSummaryEntity.getModifiedAt().atOffset(ZoneOffset.UTC));
    assertThat(actualApplicationSummary.getCreatedAt())
            .isEqualTo(expectedApplicationSummaryEntity.getCreatedAt().atOffset(ZoneOffset.UTC));
  }

  @Test
  void givenApplicationSummaryEntityWithCaseworker_whenToApplicationSummary_thenMapsAssignedToCorrectly() {

    UUID caseworkerId = UUID.randomUUID();
    CaseworkerEntity caseworkerEntity = CaseworkerEntity.builder()
            .id(caseworkerId)
            .build();

    ApplicationSummaryEntity expectedApplicationSummaryEntity = ApplicationSummaryEntity.builder()
            .createdAt(Instant.now())
            .modifiedAt(Instant.now())
            .caseworker(caseworkerEntity)
            .build();

    ApplicationSummary actualApplicationSummary = applicationSummaryMapper
            .toApplicationSummary(expectedApplicationSummaryEntity);

    assertThat(actualApplicationSummary).isNotNull();
    assertThat(actualApplicationSummary.getAssignedTo())
            .isEqualTo(caseworkerId);
  }

  @Test
  void givenApplicationSummaryEntityWithoutCaseworker_whenToApplicationSummary_thenAssignedToIsNull() {

    ApplicationSummaryEntity expectedApplicationSummaryEntity = ApplicationSummaryEntity.builder()
            .createdAt(Instant.now())
            .modifiedAt(Instant.now())
            .build();

    ApplicationSummary actualApplicationSummary = applicationSummaryMapper
            .toApplicationSummary(expectedApplicationSummaryEntity);

    assertThat(actualApplicationSummary).isNotNull();
    assertThat(actualApplicationSummary.getAssignedTo())
            .isNull();
  }

  @Test
  void givenNullApplicationEntity_whenToApplicationSummary_thenReturnNull() {

      assertThat(
              applicationSummaryMapper.toApplicationSummary(null))
              .isNull();
  }
}

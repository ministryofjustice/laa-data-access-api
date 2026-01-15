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
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();
        Instant modifiedAt = Instant.now();
        String laaReference = "ref1";
        ApplicationStatus status = ApplicationStatus.IN_PROGRESS;

        ApplicationSummaryEntity expectedApplicationSummaryEntity = ApplicationSummaryEntity.builder()
                .id(id)
                .submittedAt(createdAt)
                .modifiedAt(modifiedAt)
                .laaReference(laaReference)
                .status(status)
                .build();

        ApplicationSummary actualApplicationSummary = applicationSummaryMapper
                .toApplicationSummary(expectedApplicationSummaryEntity);

        assertThat(actualApplicationSummary).isNotNull();
        assertThat(actualApplicationSummary.getApplicationId()).isEqualTo(id);
        assertThat(actualApplicationSummary.getLaaReference()).isEqualTo(laaReference);
        assertThat(actualApplicationSummary.getStatus()).isEqualTo(status);
        assertThat(actualApplicationSummary.getLastUpdated()).isEqualTo(modifiedAt.atOffset(ZoneOffset.UTC));
        assertThat(actualApplicationSummary.getSubmittedAt()).isEqualTo(createdAt.atOffset(ZoneOffset.UTC));
    }

    @Test
    void givenApplicationSummaryEntityWithCaseworker_whenToApplicationSummary_thenMapsAssignedToCorrectly() {
        UUID caseworkerId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        Instant modifiedAt = Instant.now();

        CaseworkerEntity caseworkerEntity = CaseworkerEntity.builder()
                .id(caseworkerId)
                .build();

        ApplicationSummaryEntity expectedApplicationSummaryEntity = ApplicationSummaryEntity.builder()
                .createdAt(createdAt)
                .modifiedAt(modifiedAt)
                .caseworker(caseworkerEntity)
                .build();

        ApplicationSummary actualApplicationSummary = applicationSummaryMapper
                .toApplicationSummary(expectedApplicationSummaryEntity);

        assertThat(actualApplicationSummary).isNotNull();
        assertThat(actualApplicationSummary.getAssignedTo()).isEqualTo(caseworkerId);
    }

    @Test
    void givenApplicationSummaryEntityWithoutCaseworker_whenToApplicationSummary_thenAssignedToIsNull() {
        Instant createdAt = Instant.now();
        Instant modifiedAt = Instant.now();

        ApplicationSummaryEntity expectedApplicationSummaryEntity = ApplicationSummaryEntity.builder()
                .createdAt(createdAt)
                .modifiedAt(modifiedAt)
                .build();

        ApplicationSummary actualApplicationSummary = applicationSummaryMapper
                .toApplicationSummary(expectedApplicationSummaryEntity);

        assertThat(actualApplicationSummary).isNotNull();
        assertThat(actualApplicationSummary.getAssignedTo()).isNull();
    }

    @Test
    void givenNullApplicationEntity_whenToApplicationSummary_thenReturnNull() {
        ApplicationSummaryEntity entity = null;
        assertThat(applicationSummaryMapper.toApplicationSummary(entity)).isNull();
    }
}
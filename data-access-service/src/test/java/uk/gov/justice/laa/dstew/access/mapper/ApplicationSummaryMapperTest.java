package uk.gov.justice.laa.dstew.access.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;

@ExtendWith(MockitoExtension.class)
public class ApplicationSummaryMapperTest {

  @InjectMocks
  private ApplicationSummaryMapper applicationMapper = new ApplicationSummaryMapperImpl();

  @Test
  void shouldMapApplicationSummaryEntityToApplicationSummary() {
    UUID id = UUID.randomUUID();
    ApplicationSummaryEntity entity = new ApplicationSummaryEntity();
    entity.setId(id);
    entity.setCreatedAt(Instant.now());
    entity.setModifiedAt(Instant.now());
    entity.setApplicationReference("ref1");
    entity.setStatus(ApplicationStatus.IN_PROGRESS);

    ApplicationSummary result = applicationMapper.toApplicationSummary(entity);

    assertThat(result).isNotNull();
    assertThat(result.getApplicationId()).isEqualTo(id);
    assertThat(result.getApplicationReference()).isEqualTo("ref1");
    assertThat(result.getApplicationStatus()).isEqualTo(ApplicationStatus.IN_PROGRESS);
    // assertThat(result.getCreatedAt()).isEqualTo(entity.getCreatedAt());
    // assertThat(result.getModifiedAt()).isEqualTo(entity.getModifiedAt());
  }
}

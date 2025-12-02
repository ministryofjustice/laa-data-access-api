package uk.gov.justice.laa.dstew.access.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;


@ExtendWith(MockitoExtension.class)
public class ApplicationSummaryMapperTest {

  @InjectMocks
  private ApplicationSummaryMapper applicationMapper = new ApplicationSummaryMapperImpl();

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = "95bb88f1-99ca-4ecf-b867-659b55a8cf93")
  void shouldMapApplicationSummaryEntityToApplicationSummary(UUID caseworkerId) {
    UUID id = UUID.randomUUID();
    ApplicationSummaryEntity entity = new ApplicationSummaryEntity();
    entity.setId(id);
    entity.setCreatedAt(Instant.now());
    entity.setModifiedAt(Instant.now());
    entity.setApplicationReference("ref1");
    entity.setStatus(ApplicationStatus.IN_PROGRESS);
    entity.setCaseworker(CaseworkerEntity.builder().id(caseworkerId).build());

    ApplicationSummary result = applicationMapper.toApplicationSummary(entity);

    assertThat(result).isNotNull();
    assertThat(result.getApplicationId()).isEqualTo(id);
    assertThat(result.getApplicationReference()).isEqualTo("ref1");
    assertThat(result.getApplicationStatus()).isEqualTo(ApplicationStatus.IN_PROGRESS);
    assertThat(result.getAssignedTo() == caseworkerId);
    assertThat(result.getModifiedAt()).isEqualTo(entity.getModifiedAt().atOffset(ZoneOffset.UTC));
    assertThat(result.getCreatedAt()).isEqualTo(entity.getCreatedAt().atOffset(ZoneOffset.UTC));
  }

  @Test
  void shouldReturnNullWhenMappingNullEntity() {
    assertThat(applicationMapper.toApplicationSummary(null)).isNull();
  }

  
}

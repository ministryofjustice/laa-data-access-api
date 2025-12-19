package uk.gov.justice.laa.dstew.access.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
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
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.Status;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.model.ApplicationType;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.MatterType;


@ExtendWith(MockitoExtension.class)
public class ApplicationSummaryMapperTest {

  @InjectMocks
  private ApplicationSummaryMapper applicationMapper = new ApplicationSummaryMapperImpl();

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = "95bb88f1-99ca-4ecf-b867-659b55a8cf93")
  void shouldMapApplicationSummaryEntityToApplicationSummary(UUID caseworkerId) {
    UUID id = UUID.randomUUID();
    IndividualEntity individual = IndividualEntity.builder()
                                                  .firstName("John").lastName("Doe")
                                                  .dateOfBirth(LocalDate.of(1980, 5, 2))
                                                  .build();
    ApplicationSummaryEntity entity = new ApplicationSummaryEntity();
    entity.setId(id);
    entity.setCreatedAt(Instant.now());
    entity.setModifiedAt(Instant.now());
    entity.setSubmittedAt(Instant.now());
    entity.setAutoGranted(true);
    entity.setCategoryOfLaw(CategoryOfLaw.FAMILY);
    entity.setMatterType(MatterType.SCA);
    entity.setUsedDelegatedFunctions(true);
    entity.setLaaReference("ref1");
    entity.setStatus(Status.IN_PROGRESS);
    entity.setCaseworker(CaseworkerEntity.builder().id(caseworkerId).build());
    entity.setIndividuals(Set.of(individual));

    ApplicationSummary result = applicationMapper.toApplicationSummary(entity);

    assertThat(result).isNotNull();
    assertThat(result.getApplicationId()).isEqualTo(id);
    assertThat(result.getCreatedAt()).isEqualTo(entity.getCreatedAt().atOffset(ZoneOffset.UTC));
    assertThat(result.getLastUpdated()).isEqualTo(entity.getModifiedAt().atOffset(ZoneOffset.UTC));
    assertThat(result.getSubmittedAt()).isEqualTo(entity.getSubmittedAt().atOffset(ZoneOffset.UTC));
    assertThat(result.getAutoGrant()).isTrue();
    assertThat(result.getCategoryOfLaw()).isEqualTo(CategoryOfLaw.FAMILY);
    assertThat(result.getMatterType()).isEqualTo(MatterType.SCA);
    assertThat(result.getUsedDelegatedFunctions()).isTrue();
    assertThat(result.getLaaReference()).isEqualTo("ref1");
    assertThat(result.getStatus()).isEqualTo(Status.IN_PROGRESS);
    assertThat(result.getAssignedTo() == caseworkerId);
    assertThat(result.getClientFirstName()).isEqualTo("John");
    assertThat(result.getClientLastName()).isEqualTo("Doe");
    assertThat(result.getClientDateOfBirth()).isEqualTo(LocalDate.of(1980, 5, 2));
    assertThat(result.getApplicationType()).isEqualTo(ApplicationType.INITIAL);
  }

  @Test
  void shouldReturnNullWhenMappingNullEntity() {
    assertThat(applicationMapper.toApplicationSummary(null)).isNull();
  }

  
}

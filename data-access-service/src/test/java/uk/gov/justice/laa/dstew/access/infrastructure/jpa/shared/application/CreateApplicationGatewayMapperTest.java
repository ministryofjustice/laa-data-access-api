package uk.gov.justice.laa.dstew.access.infrastructure.jpa.shared.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domain.ApplicationDomainGenerator;

class CreateApplicationGatewayMapperTest {

  private ApplicationGatewayMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new ApplicationGatewayMapper(new ObjectMapper());
  }

  @Test
  void toDomain_mapsAllFieldsFromEntity() {
    ApplicationEntity entity = DataGenerator.createDefault(ApplicationEntityGenerator.class);
    ApplicationDomain domain = mapper.toDomain(entity);

    assertThat(domain.id()).isEqualTo(entity.getId());
    assertThat(domain.status()).isEqualTo(entity.getStatus().name());
    assertThat(domain.laaReference()).isEqualTo(entity.getLaaReference());
    assertThat(domain.officeCode()).isEqualTo(entity.getOfficeCode());
    assertThat(domain.schemaVersion()).isEqualTo(entity.getSchemaVersion());
    assertThat(domain.createdAt()).isEqualTo(entity.getCreatedAt());
    assertThat(domain.applyApplicationId()).isEqualTo(entity.getApplyApplicationId());
    assertThat(domain.submittedAt()).isEqualTo(entity.getSubmittedAt());
    assertThat(domain.usedDelegatedFunctions()).isEqualTo(entity.getUsedDelegatedFunctions());
    assertThat(domain.categoryOfLaw()).isEqualTo(entity.getCategoryOfLaw().name());
    assertThat(domain.matterType()).isEqualTo(entity.getMatterType().name());
    assertThat(domain.isAutoGranted()).isEqualTo(entity.getIsAutoGranted());
  }

  @Test
  void toEntity_mapsAllFieldsFromDomain_insertPath() {
    ApplicationDomain domain =
        DataGenerator.createDefault(
            ApplicationDomainGenerator.class,
            b -> b.id(null).createdAt(null)); // simulate pre-save state

    ApplicationEntity entity = mapper.toEntity(domain);

    assertThat(entity.getId()).isNull();
    assertThat(entity.getStatus().name()).isEqualTo(domain.status());
    assertThat(entity.getLaaReference()).isEqualTo(domain.laaReference());
    assertThat(entity.getOfficeCode()).isEqualTo(domain.officeCode());
    assertThat(entity.getSchemaVersion()).isEqualTo(domain.schemaVersion());
    assertThat(entity.getApplyApplicationId()).isEqualTo(domain.applyApplicationId());
    assertThat(entity.getSubmittedAt()).isEqualTo(domain.submittedAt());
    assertThat(entity.getUsedDelegatedFunctions()).isEqualTo(domain.usedDelegatedFunctions());
    assertThat(entity.getCategoryOfLaw().name()).isEqualTo(domain.categoryOfLaw());
    assertThat(entity.getMatterType().name()).isEqualTo(domain.matterType());
  }

  @Test
  void enrichWithSavedFields_populatesIdAndCreatedAt() {
    ApplicationDomain domain =
        DataGenerator.createDefault(
            ApplicationDomainGenerator.class, b -> b.id(null).createdAt(null));
    ApplicationEntity saved = DataGenerator.createDefault(ApplicationEntityGenerator.class);

    ApplicationDomain enriched = mapper.enrichWithSavedFields(domain, saved);

    assertThat(enriched.id()).isEqualTo(saved.getId());
    assertThat(enriched.createdAt()).isEqualTo(saved.getCreatedAt());
  }
}

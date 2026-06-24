package uk.gov.justice.laa.dstew.access.infrastructure.jpa.assigncaseworker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.caseworker.CaseworkerGenerator;

class AssignCaseworkerGatewayMapperTest {

  private AssignCaseworkerGatewayMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new AssignCaseworkerGatewayMapper();
  }

  @Test
  void givenEntityWithCaseworker_whenMapped_thenIdAndCaseworkerIdMapped() {
    UUID appId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    CaseworkerEntity caseworker =
        DataGenerator.createDefault(CaseworkerGenerator.class, b -> b.id(caseworkerId));
    ApplicationEntity entity = ApplicationEntity.builder().id(appId).caseworker(caseworker).build();

    ApplicationDomain domain = mapper.toApplicationDomain(entity);

    ApplicationDomain expected =
        ApplicationDomain.builder().id(appId).caseworkerId(caseworkerId).build();
    assertThat(domain)
        .usingRecursiveComparison()
        .comparingOnlyFields("id", "caseworkerId")
        .isEqualTo(expected);
  }

  @Test
  void givenEntityWithNullCaseworker_whenMapped_thenCaseworkerIdIsNull() {
    UUID appId = UUID.randomUUID();
    ApplicationEntity entity = ApplicationEntity.builder().id(appId).caseworker(null).build();

    ApplicationDomain domain = mapper.toApplicationDomain(entity);

    assertThat(domain.id()).isEqualTo(appId);
    assertThat(domain.caseworkerId()).isNull();
  }
}

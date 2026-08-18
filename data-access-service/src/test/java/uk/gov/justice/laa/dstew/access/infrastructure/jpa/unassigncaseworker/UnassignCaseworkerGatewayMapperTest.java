package uk.gov.justice.laa.dstew.access.infrastructure.jpa.unassigncaseworker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.caseworker.CaseworkerGenerator;

class UnassignCaseworkerGatewayMapperTest {

  private final UnassignCaseworkerGatewayMapper mapper = new UnassignCaseworkerGatewayMapper();

  @Test
  void givenEntityWithCaseworker_whenMapped_thenCaseworkerIdIsSet() {
    CaseworkerEntity caseworker = DataGenerator.createDefault(CaseworkerGenerator.class);
    ApplicationEntity application =
        DataGenerator.createDefault(
            ApplicationEntityGenerator.class, builder -> builder.caseworker(caseworker));

    ApplicationDomain domain = mapper.toApplicationDomain(application);

    assertThat(domain.id()).isEqualTo(application.getId());
    assertThat(domain.caseworkerId()).isEqualTo(caseworker.getId());
  }

  @Test
  void givenEntityWithNullCaseworker_whenMapped_thenCaseworkerIdIsNull() {
    ApplicationEntity application =
        DataGenerator.createDefault(
            ApplicationEntityGenerator.class, builder -> builder.caseworker(null));

    ApplicationDomain domain = mapper.toApplicationDomain(application);

    assertThat(domain.id()).isEqualTo(application.getId());
    assertThat(domain.caseworkerId()).isNull();
  }
}

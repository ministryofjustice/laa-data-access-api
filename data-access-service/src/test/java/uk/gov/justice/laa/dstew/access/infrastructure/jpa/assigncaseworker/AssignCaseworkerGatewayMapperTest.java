package uk.gov.justice.laa.dstew.access.infrastructure.jpa.assigncaseworker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.usecase.assigncaseworker.model.AssignCaseworkerApplication;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
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
    ApplicationEntity entity =
        DataGenerator.createDefault(
            ApplicationEntityGenerator.class, b -> b.id(appId).caseworker(caseworker));

    AssignCaseworkerApplication caseworkerAssignment = mapper.toReadModel(entity);

    assertThat(caseworkerAssignment.id()).isEqualTo(appId);
    assertThat(caseworkerAssignment.caseworkerId()).isEqualTo(caseworkerId);
  }

  @Test
  void givenEntityWithNullCaseworker_whenMapped_thenCaseworkerIdIsNull() {
    UUID appId = UUID.randomUUID();
    ApplicationEntity entity =
        DataGenerator.createDefault(
            ApplicationEntityGenerator.class, b -> b.id(appId).caseworker(null));

    AssignCaseworkerApplication caseworkerAssignment = mapper.toReadModel(entity);

    assertThat(caseworkerAssignment.id()).isEqualTo(appId);
    assertThat(caseworkerAssignment.caseworkerId()).isNull();
  }
}

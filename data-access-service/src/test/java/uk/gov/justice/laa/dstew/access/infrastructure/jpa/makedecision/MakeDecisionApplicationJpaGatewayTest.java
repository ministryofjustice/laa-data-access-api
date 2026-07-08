package uk.gov.justice.laa.dstew.access.infrastructure.jpa.makedecision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domain.ApplicationDomainGenerator;

@ExtendWith(MockitoExtension.class)
class MakeDecisionApplicationJpaGatewayTest {

  @Mock private ApplicationRepository applicationRepository;
  @Mock private MakeDecisionGatewayMapper makeDecisionGatewayMapper;

  private MakeDecisionApplicationJpaGateway gateway;

  @BeforeEach
  void setUp() {
    gateway =
        new MakeDecisionApplicationJpaGateway(applicationRepository, makeDecisionGatewayMapper);
  }

  // ── updateDecision ────────────────────────────────────────────────────────

  @Test
  void givenApplicationExists_whenUpdateDecision_thenLoadsEntityAppliesMapperAndSaves() {
    UUID applicationId = UUID.randomUUID();
    ApplicationDomain domain =
        DataGenerator.createDefault(ApplicationDomainGenerator.class, b -> b.id(applicationId));
    ApplicationEntity entity =
        ApplicationEntity.builder().status(ApplicationStatus.APPLICATION_IN_PROGRESS).build();

    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(entity));

    gateway.updateDecision(domain);

    verify(makeDecisionGatewayMapper).applyDecisionToEntity(entity, domain);
    ArgumentCaptor<ApplicationEntity> savedCaptor =
        ArgumentCaptor.forClass(ApplicationEntity.class);
    verify(applicationRepository).save(savedCaptor.capture());
    assertThat(savedCaptor.getValue()).isSameAs(entity);
  }

  @Test
  void givenApplicationDoesNotExist_whenUpdateDecision_thenThrowsIllegalStateException() {
    UUID applicationId = UUID.randomUUID();
    ApplicationDomain domain =
        DataGenerator.createDefault(ApplicationDomainGenerator.class, b -> b.id(applicationId));

    when(applicationRepository.findById(applicationId)).thenReturn(Optional.empty());

    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> gateway.updateDecision(domain))
        .withMessageContaining(applicationId.toString());

    verify(makeDecisionGatewayMapper, org.mockito.Mockito.never())
        .applyDecisionToEntity(any(), any());
  }
}

package uk.gov.justice.laa.dstew.access.infrastructure.jpa.shared.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
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
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;

@ExtendWith(MockitoExtension.class)
class ApplicationJpaGatewayTest {

  @Mock private ApplicationRepository applicationRepository;

  private ApplicationJpaGateway applicationJpaGateway;

  @BeforeEach
  void setUp() {
    applicationJpaGateway =
        new ApplicationJpaGateway(applicationRepository, new ApplicationGatewayMapper());
  }

  @Test
  void givenMissingApplication_whenFindByApplicationId_thenReturnsEmptyOptional() {
    UUID applicationId = UUID.randomUUID();
    when(applicationRepository.findById(applicationId)).thenReturn(Optional.empty());

    Optional<ApplicationDomain> actualDomain =
        applicationJpaGateway.findByApplicationId(applicationId);

    assertThat(actualDomain).isEmpty();
  }

  @Test
  void givenExistingApplication_whenFindByApplicationId_thenReturnsMappedDomain() {
    UUID applicationId = UUID.randomUUID();
    ApplicationEntity applicationEntity =
        ApplicationEntity.builder()
            .id(applicationId)
            .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
            .applicationContent(new HashMap<>(Map.of("test", "existing")))
            .build();
    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(applicationEntity));

    Optional<ApplicationDomain> actualDomain =
        applicationJpaGateway.findByApplicationId(applicationId);

    assertThat(actualDomain).isPresent();
    assertThat(actualDomain.get().id()).isEqualTo(applicationId);
    assertThat(actualDomain.get().status())
        .isEqualTo(ApplicationStatus.APPLICATION_IN_PROGRESS.name());
    assertThat(actualDomain.get().applicationContent())
        .usingRecursiveComparison()
        .isEqualTo(applicationEntity.getApplicationContent());
  }

  @Test
  void givenMissingApplication_whenUpdate_thenThrowsResourceNotFoundException() {
    UUID applicationId = UUID.randomUUID();
    String status = ApplicationStatus.APPLICATION_SUBMITTED.name();
    Map<String, Object> applicationContent = new HashMap<>(Map.of("test", "changed"));
    when(applicationRepository.findById(applicationId)).thenReturn(Optional.empty());

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> applicationJpaGateway.update(applicationId, status, applicationContent))
        .withMessageContaining("No application found with id: " + applicationId);
  }

  @Test
  void givenNullStatusInUpdatedFields_whenUpdate_thenRetainsExistingStatusAndUpdatesOtherFields() {
    UUID applicationId = UUID.randomUUID();
    ApplicationEntity existingEntity =
        ApplicationEntity.builder()
            .id(applicationId)
            .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
            .applicationContent(new HashMap<>(Map.of("test", "existing")))
            .build();
    Map<String, Object> updatedContent = new HashMap<>(Map.of("test", "changed"));

    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(existingEntity));
    when(applicationRepository.save(any(ApplicationEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    ApplicationDomain actualDomain =
        applicationJpaGateway.update(applicationId, null, updatedContent);

    ArgumentCaptor<ApplicationEntity> savedCaptor =
        ArgumentCaptor.forClass(ApplicationEntity.class);
    verify(applicationRepository, times(1)).save(savedCaptor.capture());
    ApplicationEntity savedEntity = savedCaptor.getValue();

    assertThat(savedEntity.getStatus()).isEqualTo(ApplicationStatus.APPLICATION_IN_PROGRESS);
    assertThat(savedEntity.getApplicationContent())
        .usingRecursiveComparison()
        .isEqualTo(updatedContent);
    assertThat(savedEntity.getModifiedAt()).isNotNull();

    assertThat(actualDomain.status()).isEqualTo(ApplicationStatus.APPLICATION_IN_PROGRESS.name());
    assertThat(actualDomain.applicationContent())
        .usingRecursiveComparison()
        .isEqualTo(updatedContent);
  }

  @Test
  void givenStatusInUpdatedFields_whenUpdate_thenUpdatesStatusAndContent() {
    UUID applicationId = UUID.randomUUID();
    ApplicationEntity existingEntity =
        ApplicationEntity.builder()
            .id(applicationId)
            .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
            .applicationContent(new HashMap<>(Map.of("test", "existing")))
            .build();
    String status = ApplicationStatus.APPLICATION_SUBMITTED.name();
    Map<String, Object> updatedContent = new HashMap<>(Map.of("test", "changed"));

    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(existingEntity));
    when(applicationRepository.save(any(ApplicationEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    ApplicationDomain actualDomain =
        applicationJpaGateway.update(applicationId, status, updatedContent);

    assertThat(actualDomain.status()).isEqualTo(ApplicationStatus.APPLICATION_SUBMITTED.name());
    assertThat(actualDomain.applicationContent())
        .usingRecursiveComparison()
        .isEqualTo(updatedContent);
  }
}

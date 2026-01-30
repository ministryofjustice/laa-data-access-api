package uk.gov.justice.laa.dstew.access.service.linkedapplication;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import uk.gov.justice.laa.dstew.access.entity.LinkedApplicationEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.service.LinkedApplicationService;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;

class LinkedApplicationServiceTest extends BaseServiceTest {

  @Autowired
  private LinkedApplicationService serviceUnderTest;

  @Test
  void givenValidLinkedApplications_whenNotExisting_thenPersistLinks() {
    // given
    UUID leadId = UUID.randomUUID();
    UUID associatedId = UUID.randomUUID();

    Map<String, Object> applicationContent = Map.of(
        "allLinkedApplications",
        List.of(
            Map.of(
                "leadApplicationId", leadId.toString(),
                "associatedApplicationId", associatedId.toString()
            )
        )
    );

    when(applicationRepository.existsById(leadId)).thenReturn(true);
    when(applicationRepository.existsById(associatedId)).thenReturn(true);
    when(linkedApplicationRepository
        .existsByLeadApplicationIdAndAssociatedApplicationId(leadId, associatedId))
        .thenReturn(false);

    // when
    serviceUnderTest.processLinkedApplications(applicationContent);

    // then
    verify(linkedApplicationRepository).save(
        argThat(entity ->
            entity.getLeadApplicationId().equals(leadId)
                && entity.getAssociatedApplicationId().equals(associatedId)
        )
    );
  }

  @Test
  void givenLinkAlreadyExists_whenProcessing_thenDoNotPersist() {
    // given
    UUID leadId = UUID.randomUUID();
    UUID associatedId = UUID.randomUUID();

    Map<String, Object> applicationContent = Map.of(
        "allLinkedApplications",
        List.of(
            Map.of(
                "leadApplicationId", leadId.toString(),
                "associatedApplicationId", associatedId.toString()
            )
        )
    );

    when(applicationRepository.existsById(leadId)).thenReturn(true);
    when(applicationRepository.existsById(associatedId)).thenReturn(true);
    when(linkedApplicationRepository
        .existsByLeadApplicationIdAndAssociatedApplicationId(leadId, associatedId))
        .thenReturn(true);

    // when
    serviceUnderTest.processLinkedApplications(applicationContent);

    // then
    verify(linkedApplicationRepository, never()).save(any(LinkedApplicationEntity.class));
  }

  @Test
  void givenMissingLeadApplication_whenProcessing_thenThrowResourceNotFound() {
    // given
    UUID leadId = UUID.randomUUID();

    Map<String, Object> applicationContent = Map.of(
        "allLinkedApplications",
        List.of(
            Map.of(
                "leadApplicationId", leadId.toString(),
                "associatedApplicationId", UUID.randomUUID().toString()
            )
        )
    );

    when(applicationRepository.existsById(leadId)).thenReturn(false);

    // when / then
    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() ->
            serviceUnderTest.processLinkedApplications(applicationContent)
        )
        .withMessageContaining(leadId.toString());

    verify(linkedApplicationRepository, never()).save(any());
  }

  @Test
  void givenMissingAssociatedApplication_whenProcessing_thenThrowResourceNotFound() {
    // given
    UUID leadId = UUID.randomUUID();
    UUID associatedId = UUID.randomUUID();

    Map<String, Object> applicationContent = Map.of(
        "allLinkedApplications",
        List.of(
            Map.of(
                "leadApplicationId", leadId.toString(),
                "associatedApplicationId", associatedId.toString()
            )
        )
    );

    when(applicationRepository.existsById(leadId)).thenReturn(true);
    when(applicationRepository.existsById(associatedId)).thenReturn(false);

    // when / then
    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() ->
            serviceUnderTest.processLinkedApplications(applicationContent)
        )
        .withMessageContaining(associatedId.toString());

    verify(linkedApplicationRepository, never()).save(any());
  }
}

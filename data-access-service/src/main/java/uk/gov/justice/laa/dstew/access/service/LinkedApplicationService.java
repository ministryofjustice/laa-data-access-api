package uk.gov.justice.laa.dstew.access.service;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.entity.LinkedApplicationEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.ParsedAppContentDetails;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.LinkedApplicationRepository;

/**
 * Service class for managing linked applications.
 */
@Service
@RequiredArgsConstructor
public class LinkedApplicationService {

  private final ApplicationRepository applicationRepository;
  private final LinkedApplicationRepository linkedApplicationRepository;

  /**
   * Processes linked applications from applicationContent.
   */
  @Transactional
  public void processLinkedApplications(ParsedAppContentDetails applicationDetails) {

    Object raw = applicationDetails.allLinkedApplications();

    if (!(raw instanceof List<?> rawList) || rawList.isEmpty()) {
      return;
    }

    List<Map<String, Object>> links = rawList.stream()
        .filter(Map.class::isInstance)
        .map(e -> (Map<String, Object>) e)
        .toList();

    UUID leadApplicationId = extractUuid(
        links.get(0),
        "leadApplicationId"
    );

    if (!applicationRepository.existsById(leadApplicationId)) {
      throw new ResourceNotFoundException(
          "No application found with id: " + leadApplicationId
      );
    }

    for (Map<String, Object> link : links) {

      UUID associatedApplicationId =
          extractUuid(link, "associatedApplicationId");

      // Exclude self / duplicates
      if (leadApplicationId.equals(associatedApplicationId)) {
        continue;
      }

      if (!applicationRepository.existsById(associatedApplicationId)) {
        throw new ResourceNotFoundException(
            "No application found with id: " + associatedApplicationId
        );
      }

      boolean exists =
          linkedApplicationRepository
              .existsByLeadApplicationIdAndAssociatedApplicationId(
                  leadApplicationId,
                  associatedApplicationId
              );

      if (!exists) {
        linkedApplicationRepository.save(
            LinkedApplicationEntity.builder()
                .leadApplicationId(leadApplicationId)
                .associatedApplicationId(associatedApplicationId)
                .build()
        );
      }
    }
  }

  private UUID extractUuid(Map<String, Object> map, String key) {
    Object value = map.get(key);

    if (!(value instanceof String uuid)) {
      throw new IllegalArgumentException(
          "Invalid or missing " + key
      );
    }

    return UUID.fromString(uuid);
  }
}

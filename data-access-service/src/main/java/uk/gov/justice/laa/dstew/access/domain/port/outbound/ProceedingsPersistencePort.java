package uk.gov.justice.laa.dstew.access.domain.port.outbound;

import java.util.Map;
import java.util.UUID;

/** Driven port (outbound) for persisting proceedings extracted from application content. */
public interface ProceedingsPersistencePort {

  /**
   * Saves proceedings contained in the raw application content.
   *
   * @param applicationContent the raw application content map (the adapter deserialises it)
   * @param applicationId the owning application's ID
   */
  void saveProceedings(Map<String, Object> applicationContent, UUID applicationId);
}

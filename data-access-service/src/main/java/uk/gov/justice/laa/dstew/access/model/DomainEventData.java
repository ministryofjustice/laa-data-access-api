package uk.gov.justice.laa.dstew.access.model;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Class represents data associated with a domain event.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DomainEventData {
  private UUID applicationId;
  private UUID caseWorkerId;
  private String createdBy;
  private String eventDescription;
  private Instant createdAt;
}

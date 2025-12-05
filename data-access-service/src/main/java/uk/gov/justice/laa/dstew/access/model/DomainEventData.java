package uk.gov.justice.laa.dstew.access.model;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Class represents data associated with a domain event.
 */
@Getter
@Setter
public class DomainEventData {
  private UUID applicationId;
  private UUID caseWorkerId;
  private String createdBy;
  private String eventDescription;
  private Instant createdAt;
}

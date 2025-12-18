package uk.gov.justice.laa.dstew.access.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Class represents data associated with a domain event.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssignApplicationDomainEventDetails implements Serializable {
  private UUID applicationId;
  private UUID caseworkerId;
  private String createdBy;
  private String eventDescription;
  private Instant createdAt;
}
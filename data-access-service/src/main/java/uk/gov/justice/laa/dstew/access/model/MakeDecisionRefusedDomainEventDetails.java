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
 * Class representing data associated with a domain event.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MakeDecisionRefusedDomainEventDetails implements Serializable {
  private UUID applicationId;
  private UUID caseworkerId;
  private String request;
  private String eventDescription;
  private Instant createdAt;
}
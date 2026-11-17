package uk.gov.justice.laa.dstew.access.query.application.history;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One Prior Authority history entry, before its description is hydrated. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriorAuthorityHistoryRow {
  String eventId;
  UUID applicationId;
  UUID priorAuthorityId;
  String priorAuthorityType;
  String eventType;
  Long itemVersion;
  String serviceName;
  UUID caseworkerId;
  Instant occurredAt;
}

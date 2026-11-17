package uk.gov.justice.laa.dstew.access.query.application.history;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** One application history entry, before its description is hydrated. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ExcludeFromGeneratedCodeCoverage
public class ApplicationHistoryRow {
  private String eventId;
  private UUID applicationId;
  private String eventType;
  private Long dataVersion;
  private UUID justificationId;
  private String serviceName;
  private UUID caseworkerId;
  private Instant occurredAt;
}

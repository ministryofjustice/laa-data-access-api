package uk.gov.justice.laa.dstew.access.query.application.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only audit entry emitted by the Application event stream. */
@Entity
@Table(name = "application_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationHistoryReadModel {

  @Id
  @Column(name = "event_id")
  private String eventId;

  @Column(name = "application_id", nullable = false)
  private UUID applicationId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "request_payload", nullable = false)
  private String requestPayload;

  @Column(name = "service_name")
  private String serviceName;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;
}

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

/** JPA read model for the {@code prior_authority_history} audit table. */
@Entity
@Table(name = "prior_authority_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriorAuthorityHistoryReadModel {

  @Id
  @Column(name = "event_id")
  String eventId;

  @Column(name = "application_id", nullable = false)
  UUID applicationId;

  @Column(name = "prior_authority_id", nullable = false)
  UUID priorAuthorityId;

  @Column(name = "prior_authority_type", nullable = false)
  String priorAuthorityType;

  @Column(name = "event_type", nullable = false)
  String eventType;

  @Column(name = "item_version", nullable = false)
  Long itemVersion;

  @Column(name = "service_name")
  String serviceName;

  @Column(name = "caseworker_id")
  UUID caseworkerId;

  @Column(name = "occurred_at", nullable = false)
  Instant occurredAt;
}

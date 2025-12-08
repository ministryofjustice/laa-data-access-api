package uk.gov.justice.laa.dstew.access.entity;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;
import uk.gov.justice.laa.dstew.access.model.DomainEventData;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;

/**
 * Represents a domain events table.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "domain_events")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class DomainEventEntity {
  @Id
  @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
  @Column(columnDefinition = "UUID")
  private UUID id;

  @Column(name = "application_id")
  private UUID applicationId;

  @Column(name = "caseworker_id")
  private UUID caseWorkerId;

  @Column(name = "type", nullable = false)
  @Enumerated(EnumType.STRING)
  private DomainEventType type;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb", name = "data", nullable = false)
  private DomainEventData data;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "created_by")
  private String createdBy;
}

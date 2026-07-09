package uk.gov.justice.laa.dstew.access.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.ServiceName;

/** Represents a domain events table. */
@ExcludeFromGeneratedCodeCoverage
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
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
  private UUID caseworkerId;

  @Column(name = "type", nullable = false)
  @Enumerated(EnumType.STRING)
  private DomainEventType type;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb", name = "data", nullable = false)
  private String data;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "created_by")
  private String createdBy;

  @Column(name = "service_name")
  @Enumerated(EnumType.STRING)
  private ServiceName serviceName;
}

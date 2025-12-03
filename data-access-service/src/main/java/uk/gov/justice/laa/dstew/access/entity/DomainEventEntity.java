package uk.gov.justice.laa.dstew.access.entity;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

/**
 * Represents a domain events table.
 */
@Getter
@Setter
@RequiredArgsConstructor
@Entity
@Table(name = "domain_events")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class DomainEventEntity {
  @Id
  @Column(name = "id")
  private UUID id;

  @OneToOne
  @JoinColumn(name = "application_id", referencedColumnName = "id", nullable = false)
  private ApplicationEntity application;

  @OneToOne
  @JoinColumn(name = "caseworker_id", referencedColumnName = "id")
  private CaseworkerEntity caseworker;

  @Column(name = "type", nullable = false)
  @Enumerated(EnumType.STRING)
  private DomainEventType type;

  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb", nullable = false)
  private Map<String, Object> data;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "created_by")
  private String createdBy;
}

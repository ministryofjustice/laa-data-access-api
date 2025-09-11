package uk.gov.justice.laa.dstew.access.entity;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Represents the historical changes to an application for legal aid.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "application_history")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ApplicationHistoryEntity {

  @Id
  @GeneratedValue
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "application_id", nullable = false)
  private UUID applicationId;

  @Column(name = "user_id", nullable = false)
  private String userId;

  @Column(name = "resource_type_changed", nullable = false)
  private String resourceTypeChanged;

  @Column(name = "action", nullable = false)
  private String action;

  @CreatedDate
  @Column(name = "timestamp", nullable = false, updatable = false)
  private Instant timestamp;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "historic_snapshot", columnDefinition = "jsonb", nullable = false)
  private Map<String, Object> historicSnapshot;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "application_snapshot", columnDefinition = "jsonb", nullable = false)
  private Map<String, Object> applicationSnapshot;
}

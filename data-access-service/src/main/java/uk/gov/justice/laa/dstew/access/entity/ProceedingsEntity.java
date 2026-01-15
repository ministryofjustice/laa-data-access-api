package uk.gov.justice.laa.dstew.access.entity;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;


/**
 * Represents a proceeding.
 */
@ExcludeFromGeneratedCodeCoverage
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Entity
@Table(name = "proceedings")
@EntityListeners(AuditingEntityListener.class)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ProceedingsEntity implements AuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(columnDefinition = "UUID")
  private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "application_id", nullable = false)
  private ApplicationEntity application;

  @Column(name = "apply_proceeding_id", nullable = false)
  private UUID applyProceedingId;

  @Column(name = "description", nullable = false)
  private String description;

  @Column(name = "is_lead", nullable = false)
  private boolean isLead;

  @Type(JsonType.class)
  @Column(name = "proceeding_content", columnDefinition = "jsonb", nullable = false)
  private Map<String, Object> proceedingContent;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "created_by", nullable = false)
  private String createdBy;

  @UpdateTimestamp
  @Column(name = "modified_at", nullable = false)
  private Instant modifiedAt;

  @Column(name = "updated_by", nullable = false)
  private String updatedBy;

  @Override
  public Instant getCreatedAt() {
    return createdAt;
  }

  @Override
  public String getCreatedBy() {
    return createdBy;
  }

  @Override
  public Instant getUpdatedAt() {
    return modifiedAt;
  }

  @Override
  public String getUpdatedBy() {
    return updatedBy;
  }
}

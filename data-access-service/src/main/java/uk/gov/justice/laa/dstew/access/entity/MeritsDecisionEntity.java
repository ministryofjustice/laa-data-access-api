package uk.gov.justice.laa.dstew.access.entity;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;

/**
 * Represents a merits decision.
 */
@ExcludeFromGeneratedCodeCoverage
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Entity
@Table(name = "merits_decisions")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MeritsDecisionEntity implements AuditableEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(columnDefinition = "UUID")
  private UUID id;

  @Setter
  @Column(name = "proceeding_id", nullable = false)
  private UUID proceedingId;

  @Setter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "decisions_id", nullable = false)
  private DecisionEntity decisionEntity;

  @Setter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "proceeding_id", nullable = false, insertable = false, updatable = false)
  private ProceedingEntity proceeding;

  @Setter
  @Column(name = "created_at")
  @CreationTimestamp
  private Instant createdAt;

  @Setter
  @Column(name = "modified_at")
  @UpdateTimestamp
  private Instant modifiedAt;

  @Setter
  @Column(name = "decision", nullable = false)
  @Enumerated(EnumType.STRING)
  private MeritsDecisionStatus decision;

  @Setter
  @Column(name = "reason")
  private String reason;

  @Setter
  @Column(name = "justification")
  private String justification;

  /**
   * Custom setter that ensures proceedingId is set when proceeding is set.
   */
  public void setProceeding(ProceedingEntity proceeding) {
    this.proceeding = proceeding;
    if (proceeding != null && proceeding.getId() != null) {
      this.proceedingId = proceeding.getId();
    }
  }

  /**
   * Ensures proceedingId is synced with proceeding relationship before persistence.
   * Handles cases where test fixtures set only the proceeding relationship.
   */
  @PrePersist
  protected void ensureProceedingIdBeforePersist() {
    if (this.proceedingId == null && this.proceeding != null) {
      this.proceedingId = this.proceeding.getId();
    }
  }

  @PreUpdate
  protected void ensureProceedingIdBeforeUpdate() {
    if (this.proceedingId == null && this.proceeding != null) {
      this.proceedingId = this.proceeding.getId();
    }
  }

  @Override
  public Instant getCreatedAt() {
    return createdAt;
  }

  @Override
  public String getCreatedBy() {
    return null;
  }

  @Override
  public Instant getUpdatedAt() {
    return modifiedAt;
  }

  @Override
  public String getUpdatedBy() {
    return null;
  }
}
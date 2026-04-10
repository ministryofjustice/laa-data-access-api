package uk.gov.justice.laa.dstew.access.entity;

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
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;

/** Represents a merits decision. */
@ExcludeFromGeneratedCodeCoverage
@Getter
@Setter
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

  @Column(name = "proceeding_id", nullable = false)
  private UUID proceedingId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "proceeding_id", nullable = false, insertable = false, updatable = false)
  private ProceedingEntity proceeding;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "decisions_id", nullable = false)
  private DecisionEntity decisionEntity;

  @Column(name = "created_at")
  @CreationTimestamp
  private Instant createdAt;

  @Column(name = "modified_at")
  @UpdateTimestamp
  private Instant modifiedAt;

  @Column(name = "decision", nullable = false)
  @Enumerated(EnumType.STRING)
  private MeritsDecisionStatus decision;

  @Column(name = "reason")
  private String reason;

  @Column(name = "justification")
  private String justification;

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

  /**
   * Adds a proceeding to the merits decision and sets the proceeding ID based on the proceeding's
   * ID. If the proceeding is null or has a null ID, the proceeding ID will not be set.
   *
   * @param proceeding proceeding entity
   */
  public void setProceeding(ProceedingEntity proceeding) {
    this.proceeding = proceeding;
    if (proceeding != null && proceeding.getId() != null) {
      this.proceedingId = proceeding.getId();
    }
  }

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
}

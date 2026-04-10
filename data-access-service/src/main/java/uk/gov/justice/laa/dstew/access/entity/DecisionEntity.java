package uk.gov.justice.laa.dstew.access.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Set;
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
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;

/** Represents a decision. */
@ExcludeFromGeneratedCodeCoverage
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Entity
@Table(name = "decisions")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class DecisionEntity implements AuditableEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(columnDefinition = "UUID")
  private UUID id;

  @Column(name = "created_at")
  @CreationTimestamp
  private Instant createdAt;

  @Column(name = "modified_at")
  @UpdateTimestamp
  private Instant modifiedAt;

  @OneToMany(
      mappedBy = "decisionEntity",
      cascade = {CascadeType.PERSIST, CascadeType.MERGE},
      fetch = FetchType.LAZY)
  @lombok.Setter(lombok.AccessLevel.NONE)
  private Set<MeritsDecisionEntity> meritsDecisions;

  @Column(name = "overall_decision", nullable = false)
  @Enumerated(EnumType.STRING)
  private DecisionStatus overallDecision;

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
   * Adds a merits decision to the set of linked merits decisions. Initializes the set if it is
   * null.
   *
   * @param merit merit decision
   */
  public void addMeritsDecision(MeritsDecisionEntity merit) {
    if (meritsDecisions == null) {
      meritsDecisions = new java.util.HashSet<>();
    }
    merit.setDecisionEntity(this);
    meritsDecisions.add(merit);
  }

  /**
   * Sets the merits decisions collection and syncs the back-reference on each element.
   *
   * @param meritsDecisions the new set of merits decisions
   */
  public void setMeritsDecisions(Set<MeritsDecisionEntity> meritsDecisions) {
    this.meritsDecisions = meritsDecisions;
    if (meritsDecisions != null) {
      meritsDecisions.forEach(m -> m.setDecisionEntity(this));
    }
  }

  @PrePersist
  @PreUpdate
  protected void syncMeritsDecisionBackRefs() {
    if (meritsDecisions != null) {
      meritsDecisions.forEach(m -> m.setDecisionEntity(this));
    }
  }
}

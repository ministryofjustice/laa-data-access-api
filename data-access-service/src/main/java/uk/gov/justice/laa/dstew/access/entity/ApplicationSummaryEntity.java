package uk.gov.justice.laa.dstew.access.entity;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;

/**
 * Represents an application summary for legal aid.
 * Will be removed when merged into new application structures
 */
@Getter
@Setter
@RequiredArgsConstructor
@Entity
@Table(name = "application")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ApplicationSummaryEntity {
  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "status", nullable = false)
  @Enumerated(EnumType.STRING)
  private ApplicationStatus status;

  @Column(name = "application_reference")
  private String applicationReference;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "modified_at")
  private Instant modifiedAt;

  @ManyToMany(cascade = CascadeType.PERSIST)
  @JoinTable(
          name = "linked_individual",
          joinColumns = @JoinColumn(name = "application_id"),
          inverseJoinColumns = @JoinColumn(name = "individual_id")
  )
  private Set<IndividualEntity> individuals;

  @OneToOne
  @JoinColumn(name = "caseworker_id", referencedColumnName = "id")
  private CaseworkerEntity caseworker;

  @Column(name = "caseworker_id")
  private UUID caseworkerId;

}

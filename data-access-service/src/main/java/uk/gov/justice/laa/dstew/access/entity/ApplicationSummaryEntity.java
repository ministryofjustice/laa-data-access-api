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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationType;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.MatterType;


/**
 * Represents an application summary for legal aid.
 * Will be removed when merged into new application structures
 */
@ExcludeFromGeneratedCodeCoverage
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "applications")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ApplicationSummaryEntity {
  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "status", nullable = false)
  @Enumerated(EnumType.STRING)
  private ApplicationStatus status;

  @Column(name = "laa_reference")
  private String laaReference;

  @Column(name = "office_code")
  private String officeCode;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "modified_at")
  private Instant modifiedAt;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @Column(name = "used_delegated_functions")
  private Boolean usedDelegatedFunctions;

  @Column(name = "category_of_law")
  @Enumerated(EnumType.STRING)
  private CategoryOfLaw categoryOfLaw;

  @Column(name = "matter_types")
  @Enumerated(EnumType.STRING)
  private MatterType matterType;

  @Column(name = "is_auto_granted")
  private Boolean isAutoGranted;

  @OneToMany
  @JoinTable(
      name = "linked_applications",
      joinColumns = @JoinColumn(name = "lead_application_id"),
      inverseJoinColumns = @JoinColumn(name = "associated_application_id")
  )
  private Set<ApplicationEntity> linkedApplications;


  @Transient
  public boolean isLead() {
    return linkedApplications != null && !linkedApplications.isEmpty();
  }

  @Transient
  private ApplicationType type = ApplicationType.INITIAL;

  @ManyToMany(cascade = CascadeType.PERSIST)
  @JoinTable(
          name = "linked_individuals",
          joinColumns = @JoinColumn(name = "application_id"),
          inverseJoinColumns = @JoinColumn(name = "individual_id")
  )
  private Set<IndividualEntity> individuals;

  @OneToOne
  @JoinColumn(name = "caseworker_id", referencedColumnName = "id")
  private CaseworkerEntity caseworker;
}

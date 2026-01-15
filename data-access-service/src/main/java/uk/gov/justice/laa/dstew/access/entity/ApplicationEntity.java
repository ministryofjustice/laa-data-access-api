package uk.gov.justice.laa.dstew.access.entity;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.MatterType;

/**
 * Represents an application.
 */
@ExcludeFromGeneratedCodeCoverage
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@Entity
@Table(name = "applications")
@EntityListeners(AuditingEntityListener.class)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ApplicationEntity implements AuditableEntity {

  @Id
  @Column(columnDefinition = "UUID")
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private UUID id;

  @Column(name = "status", nullable = false)
  @Enumerated(EnumType.STRING)
  private ApplicationStatus status;

  @Column(name = "laa_reference")
  private String laaReference;

  @Column(name = "office_code")
  private String officeCode;

  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb", name = "application_content")
  private Map<String, Object> applicationContent;

  @ManyToMany(cascade = CascadeType.PERSIST)
  @JoinTable(
      name = "linked_individuals",
      joinColumns = @JoinColumn(name = "application_id"),
      inverseJoinColumns = @JoinColumn(name = "individual_id")
  )
  private Set<IndividualEntity> individuals;

  @Column(name = "schema_version")
  private Integer schemaVersion;

  @Column(name = "created_at")
  @CreationTimestamp
  private Instant createdAt;

  @Column(name = "modified_at")
  @UpdateTimestamp
  private Instant modifiedAt;

  @OneToOne()
  @JoinColumn(name = "caseworker_id", referencedColumnName = "id")
  private CaseworkerEntity caseworker;

  @Column(name = "apply_application_id")
  private UUID applyApplicationId;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @OneToOne()
  @JoinColumn(name = "decision_id", referencedColumnName = "id")
  private DecisionEntity decision;

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

  // getters and setters
  public Map<String, Object> getApplicationContent() {
    return applicationContent;
  }

  public void setApplicationContent(Map<String, Object> applicationContent) {
    this.applicationContent = applicationContent;
  }

  /**
  * adds an application to the set of linked applications.
  */
  public void addLinkedApplication(ApplicationEntity toAdd) {
    if (linkedApplications == null) {
      linkedApplications = new HashSet<>();
    }
    linkedApplications.add(toAdd);
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

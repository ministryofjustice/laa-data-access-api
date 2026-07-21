package uk.gov.justice.laa.dstew.access.query.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Replayable current-state read model holding only queryable metadata for a Application. */
@Entity
@Table(name = "application_current_state")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationReadModel {

  @Id
  @Column(name = "apply_application_id")
  private UUID applyApplicationId;

  private String status;

  @Column(name = "laa_reference")
  private String laaReference;

  @Column(name = "schema_version")
  private int schemaVersion;

  @Column(name = "application_type")
  private String applicationType;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @Column(name = "office_code")
  private String officeCode;

  @Column(name = "used_delegated_functions")
  private Boolean usedDelegatedFunctions;

  @Column(name = "category_of_law")
  private String categoryOfLaw;

  @Column(name = "matter_type")
  private String matterType;

  @Column(name = "created_at")
  private Instant createdAt;
}

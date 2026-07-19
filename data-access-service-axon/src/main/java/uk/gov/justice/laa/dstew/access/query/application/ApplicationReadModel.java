package uk.gov.justice.laa.dstew.access.query.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationIndividual;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationProceeding;

/** Replayable current-state read model for an Application. */
@Entity
@Table(name = "application_current_state")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationReadModel {

  @Id
  @Column(name = "application_id")
  private UUID applicationId;

  private String status;

  @Column(name = "application_data_version", nullable = false)
  private long applicationDataVersion;

  @Transient private String laaReference;

  @Transient private ApplicationContent applicationContent;

  @Transient private List<ApplicationIndividual> individuals;

  @Column(name = "schema_version")
  private int schemaVersion;

  @Column(name = "application_type")
  private String applicationType;

  @Column(name = "apply_application_id")
  private UUID applyApplicationId;

  @Column(name = "lead_application_id")
  private UUID leadApplicationId;

  @Transient private Instant submittedAt;

  @Transient private String officeCode;

  @Transient private Boolean usedDelegatedFunctions;

  @Transient private String categoryOfLaw;

  @Transient private String matterType;

  @Transient private List<ApplicationProceeding> proceedings;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "modified_at")
  private Instant modifiedAt;
}

package uk.gov.justice.laa.dstew.access.query.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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

  @Column(name = "laa_reference")
  private String laaReference;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "application_content")
  private ApplicationContent applicationContent;

  @JdbcTypeCode(SqlTypes.JSON)
  private List<ApplicationIndividual> individuals;

  @Column(name = "schema_version")
  private int schemaVersion;

  @Column(name = "application_type")
  private String applicationType;

  @Column(name = "apply_application_id")
  private UUID applyApplicationId;

  @Column(name = "lead_application_id")
  private UUID leadApplicationId;

  @Column(name = "is_lead")
  private Boolean isLead;

  @Column(name = "group_id")
  private UUID groupId;

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

  @JdbcTypeCode(SqlTypes.JSON)
  private List<ApplicationProceeding> proceedings;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "modified_at")
  private Instant modifiedAt;
}

package uk.gov.justice.laa.dstew.access.query.application.summary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;

/**
 * The application summary: the one read model holding descriptive and filterable facts about each
 * application. It is built from event content by {@link ApplicationSummaryProjection}, so erasure
 * plus a rebuild only has to reach this table. Other read sides look facts up through {@link
 * ApplicationSummaries}; only the list page queries it directly, because it filters and sorts on
 * these fields. Workflow-only state, such as assignment versions, does not belong here.
 */
@Entity
@Table(name = "application_summary")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ExcludeFromGeneratedCodeCoverage
public class ApplicationSummaryReadModel {

  @Id
  @Column(name = "application_id")
  private UUID applicationId;

  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "laa_reference")
  private String laaReference;

  @Column(name = "caseworker_id")
  private UUID caseworkerId;

  @Column(name = "matter_type")
  private String matterType;

  @Column(name = "category_of_law")
  private String categoryOfLaw;

  @Column(name = "used_delegated_functions")
  private Boolean usedDelegatedFunctions;

  /** Distinct proceeding matter types, paired by position with {@link #matterTypeCodes}. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "matter_types")
  private List<String> matterTypes;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "matter_type_codes")
  private List<String> matterTypeCodes;

  @Column(name = "category_of_law_code")
  private String categoryOfLawCode;

  @Column(name = "matter_type_code")
  private String matterTypeCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "auto_granted", nullable = false)
  private AutoGrantedState autoGranted;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @Column(name = "modified_at", nullable = false)
  private Instant modifiedAt;

  @Column(name = "linked_group_id")
  private UUID linkedGroupId;

  @Column(name = "lead_application_id")
  private UUID leadApplicationId;

  @Column(name = "client_first_name")
  private String clientFirstName;

  @Column(name = "client_last_name")
  private String clientLastName;

  @Column(name = "client_date_of_birth")
  private LocalDate clientDateOfBirth;

  @Column(name = "office_code")
  private String officeCode;

  /**
   * The application-domain version at the time this row was last written. Used as an optimistic
   * concurrency guard during projection updates.
   */
  @Column(name = "stream_version", nullable = false)
  private long streamVersion;

  /**
   * The Axon global event index at which this row was last written. Used to measure projection lag
   * relative to {@code domain_event_entry.global_index}.
   */
  @Column(name = "projection_position", nullable = false)
  private long projectionPosition;
}

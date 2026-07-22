package uk.gov.justice.laa.dstew.access.query.application.listindex;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Replayable list-index read model used exclusively for filtering, sorting, counting, and paging
 * the {@code GET /applications} endpoint.
 *
 * <p>All columns are persisted — there are no {@code @Transient} fields. The minimum PII needed for
 * client-name and date-of-birth filters ({@code client_first_name}, {@code client_last_name},
 * {@code client_date_of_birth}) is stored here so filters can be pushed entirely to the database.
 * Rich response-only fields (proceedings, certificate, application content, etc.) are not
 * duplicated into this table; they are bulk-loaded from {@code application_data} for the result
 * page only, after paging has been applied.
 */
@Entity
@Table(name = "application_list_index")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationListIndexReadModel {

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

  @Column(name = "is_auto_granted")
  private Boolean isAutoGranted;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @Column(name = "lead_application_id")
  private UUID leadApplicationId;

  @Column(name = "client_first_name")
  private String clientFirstName;

  @Column(name = "client_last_name")
  private String clientLastName;

  @Column(name = "client_date_of_birth")
  private LocalDate clientDateOfBirth;

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

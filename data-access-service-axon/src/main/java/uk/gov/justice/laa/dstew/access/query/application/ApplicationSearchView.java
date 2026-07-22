package uk.gov.justice.laa.dstew.access.query.application;

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

@Entity
@Table(name = "application_search_view")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationSearchView {

  @Id
  @Column(name = "application_id")
  private UUID applicationId;

  @Column(name = "stream_version")
  private Long streamVersion;

  @Column(name = "status")
  private String status;

  @Column(name = "laa_reference")
  private String laaReference;

  @Column(name = "schema_version")
  private Integer schemaVersion;

  @Column(name = "client_first_name")
  private String clientFirstName;

  @Column(name = "client_last_name")
  private String clientLastName;

  @Column(name = "client_date_of_birth")
  private LocalDate clientDateOfBirth;

  @Column(name = "caseworker_id")
  private String caseworkerId;

  @Column(name = "matter_type")
  private String matterType;

  @Column(name = "category_of_law")
  private String categoryOfLaw;

  @Column(name = "is_auto_granted")
  private Boolean isAutoGranted;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "modified_at")
  private Instant modifiedAt;

  @Column(name = "lead_application_id")
  private UUID leadApplicationId;

  @Column(name = "is_lead")
  private Boolean isLead;

  @Column(name = "projection_position")
  private Long projectionPosition;
}

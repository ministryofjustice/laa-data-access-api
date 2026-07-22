package uk.gov.justice.laa.dstew.access.query.application.search;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Link-table projection row for linked-application membership. */
@Entity
@Table(name = "application_link_search_view")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationLinkSearchView {

  @EmbeddedId private ApplicationLinkId id;

  @Column(name = "stream_version")
  private Long streamVersion;

  @Column(name = "projection_position")
  private Long projectionPosition;

  public UUID getLeadApplicationId() {
    return id == null ? null : id.leadApplicationId;
  }

  public UUID getApplicationId() {
    return id == null ? null : id.applicationId;
  }

  @Embeddable
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ApplicationLinkId implements Serializable {
    @Column(name = "lead_application_id")
    private UUID leadApplicationId;

    @Column(name = "application_id")
    private UUID applicationId;
  }
}

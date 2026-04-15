package uk.gov.justice.laa.dstew.access.domain.model;

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
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.MatterType;

/**
 * Domain model representing an application. Free of persistence and framework annotations — this is
 * the core domain object used by use cases.
 */
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Application {
  private UUID id;
  private Long version;
  private ApplicationStatus status;
  private String laaReference;
  private String officeCode;
  private Map<String, Object> applicationContent;
  private Set<Individual> individuals;
  private Integer schemaVersion;
  private Instant createdAt;
  private Instant modifiedAt;
  private UUID applyApplicationId;
  private Instant submittedAt;
  private Boolean usedDelegatedFunctions;
  private CategoryOfLaw categoryOfLaw;
  private MatterType matterType;
  private Set<Application> linkedApplications;

  /** Adds an application to the set of linked applications. */
  public void addLinkedApplication(Application toAdd) {
    if (linkedApplications == null) {
      linkedApplications = new HashSet<>();
    }
    linkedApplications.add(toAdd);
  }
}

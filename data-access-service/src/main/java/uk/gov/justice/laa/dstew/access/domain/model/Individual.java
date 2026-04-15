package uk.gov.justice.laa.dstew.access.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.gov.justice.laa.dstew.access.model.IndividualType;

/**
 * Domain model representing an individual linked to an application. Free of persistence and
 * framework annotations.
 */
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Individual {
  private UUID id;
  private String firstName;
  private String lastName;
  private LocalDate dateOfBirth;
  private Map<String, Object> individualContent;
  private IndividualType type;
  private Instant createdAt;
  private Instant modifiedAt;
}

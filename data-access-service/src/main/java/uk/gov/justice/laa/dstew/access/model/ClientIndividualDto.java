package uk.gov.justice.laa.dstew.access.model;

import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/**
 * DTO for client individual data linked to an application.
 * Contains only basic client information without the full individualContent JSON blob.
 */
@Builder(toBuilder = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@ExcludeFromGeneratedCodeCoverage
public class ClientIndividualDto {
  private UUID applicationId;
  private String firstName;
  private String lastName;
  private LocalDate dateOfBirth;
}

